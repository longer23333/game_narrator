using System.Diagnostics;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Net;
using System.Net.Sockets;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace GameNarrator.Launcher;

internal static class Program {
    private static readonly string AppVersion = Application.ProductVersion;
    private static int AppPort = 18081;
    private static int OllamaPort = 11434;
    private const string Model = "qwen2.5vl:3b";
    private const string OllamaUrl = "https://github.com/ollama/ollama/releases/download/v0.32.5/ollama-windows-amd64.zip";
    private const string OllamaSha256 = "7c941ae084569d298062d29f8139163a3187c76dbca0479c70d085e78fd8c7bb";
    private static readonly List<Process> Children = [];
    private static string? DesktopLogPath;
    private const string ActivationEventName = "Local\\GameNarrator.Desktop.Activate";
    private static StartupForm? Window;

    [STAThread]
    private static void Main() {
        using var mutex = new Mutex(true, "Local\\GameNarrator.Desktop.Singleton", out var first);
        using var activation = new EventWaitHandle(false, EventResetMode.AutoReset, ActivationEventName);
        if (!first) { activation.Set(); return; }
        ApplicationConfiguration.Initialize();
        Application.ApplicationExit += (_, _) => StopChildren();
        Window = new StartupForm();
        var activationThread = new Thread(() => {
            while (activation.WaitOne()) {
                if (Window.IsDisposed) return;
                Window.BeginInvoke(Window.ShowDesktopWindow);
            }
        }) { IsBackground=true, Name="GameNarrator activation listener" };
        activationThread.Start();
        Application.Run(Window);
    }

    private sealed class StartupForm : Form {
        private readonly Label status = new() { AutoSize=false, Dock=DockStyle.Top, Height=70, TextAlign=ContentAlignment.MiddleCenter };
        private readonly ProgressBar progress = new() { Dock=DockStyle.Top, Height=18, Style=ProgressBarStyle.Marquee };
        private readonly Button retry = new() { Text="重试", Dock=DockStyle.Bottom, Height=42, Visible=false };
        private readonly WebView2 webView = new() { Dock=DockStyle.Fill, Visible=false };
        private readonly NotifyIcon tray;
        private CoreWebView2Environment? webViewEnvironment;
        private bool bilibiliLoginRunning;

        internal StartupForm() {
            Text=$"GameNarrator {AppVersion}"; Width=520; Height=210; StartPosition=FormStartPosition.CenterScreen;
            MinimumSize=new Size(1024,720); FormBorderStyle=FormBorderStyle.FixedDialog; MaximizeBox=false;
            Controls.Add(webView); Controls.Add(retry); Controls.Add(progress); Controls.Add(status);
            var menu=new ContextMenuStrip(); menu.Items.Add("打开 GameNarrator",null,(_,_)=>ShowDesktopWindow()); menu.Items.Add("退出",null,(_,_)=>Close());
            var appIcon=Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;
            Icon=appIcon;
            tray=new NotifyIcon{Icon=appIcon,Text=$"GameNarrator {AppVersion}",ContextMenuStrip=menu,Visible=true}; tray.DoubleClick+=(_,_)=>ShowDesktopWindow();
            retry.Click += async (_, _) => await StartAsync();
            Shown += async (_, _) => await StartAsync();
        }

        private async Task StartAsync() {
            retry.Visible=false; progress.Visible=true;
            try {
                StopChildren();
                var root=AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
                var data=Path.Combine(root,"data");
                Directory.CreateDirectory(Path.Combine(data,"logs")); Directory.CreateDirectory(Path.Combine(data,"storage"));
                DesktopLogPath=Path.Combine(data,"logs","desktop.log");
                DesktopLog("STARTUP_BEGIN");
                Directory.CreateDirectory(Path.Combine(data,"config")); Directory.CreateDirectory(Path.Combine(data,"runtime"));
                AppPort=FindFreePort(18081); OllamaPort=FindFreePort(11434,AppPort);
                DesktopLog($"PORTS_SELECTED appPort={AppPort} ollamaPort={OllamaPort}");
                Environment.SetEnvironmentVariable("GAME_NARRATOR_APP_ROOT", root);
                Environment.SetEnvironmentVariable("GAME_NARRATOR_DATA_ROOT", data);
                Environment.SetEnvironmentVariable("OLLAMA_MODELS", Path.Combine(data,"models","ollama"));
                Environment.SetEnvironmentVariable("OLLAMA_HOST", $"127.0.0.1:{OllamaPort}");
                Environment.SetEnvironmentVariable("OLLAMA_BASE_URL", $"http://127.0.0.1:{OllamaPort}");
                Environment.SetEnvironmentVariable("OLLAMA_NUM_PARALLEL", "1");
                Environment.SetEnvironmentVariable("OLLAMA_MAX_LOADED_MODELS", "1");
                Environment.SetEnvironmentVariable("OLLAMA_KEEP_ALIVE", "2m");
                Environment.SetEnvironmentVariable("OLLAMA_FLASH_ATTENTION", "1");
                Environment.SetEnvironmentVariable("SERVER_PORT", AppPort.ToString());
                var processors=Math.Max(1,Environment.ProcessorCount);
                var availableMemoryMb=Math.Max(1024,GC.GetGCMemoryInfo().TotalAvailableMemoryBytes/1024/1024);
                var javaHeapMb=availableMemoryMb>=16384 ? 1024 : availableMemoryMb>=8192 ? 768 : 512;
                Environment.SetEnvironmentVariable("WHISPER_THREADS",Math.Clamp(processors-1,1,8).ToString());
                Environment.SetEnvironmentVariable("ASYNC_CORE_POOL_SIZE",(processors>=8 ? 2 : 1).ToString());
                Environment.SetEnvironmentVariable("ASYNC_MAX_POOL_SIZE",(processors>=12 ? 4 : 2).ToString());
                Environment.SetEnvironmentVariable("SERVER_MAX_THREADS",(processors>=12 ? 48 : 32).ToString());
                DesktopLog($"RUNTIME_PROFILE processors={processors} availableMemoryMb={availableMemoryMb} javaHeapMb={javaHeapMb} whisperThreads={Math.Clamp(processors-1,1,8)}");
                File.WriteAllText(Path.Combine(data,"runtime","app-port"),AppPort.ToString());
                File.WriteAllText(Path.Combine(data,"config","runtime.json"),JsonSerializer.Serialize(new {
                    version=AppVersion,appRoot=root,dataRoot=data,storageRoot=Path.Combine(data,"storage"),appPort=AppPort,ollamaPort=OllamaPort
                },new JsonSerializerOptions{WriteIndented=true}));
                status.Text="首次运行：正在安装本地 AI 引擎（支持断点续传）…";
                status.Text = WantsLocalAi(data) ? "正在准备本地 AI…" : "正在使用云端 AI 启动…";
                if (WantsLocalAi(data)) {
                if (!File.Exists(Path.Combine(data,"tools","ollama","ollama.exe")) &&
                    MessageBox.Show("本地 AI 尚未安装，需要联网下载约 2GB。现在安装吗？","安装本地 AI",
                        MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)
                    throw new InvalidOperationException("已取消本地 AI 安装，可在 AI 模型设置中切换为云端模式。");
                progress.Style=ProgressBarStyle.Continuous; progress.Minimum=0; progress.Maximum=100; progress.Value=0;
                var downloadProgress=new Progress<int>(value=>{progress.Value=Math.Clamp(value,0,100);status.Text=$"首次运行：正在安装本地 AI 引擎… {value}%";});
                var ollama = await EnsureOllama(data,downloadProgress);
                status.Text="正在启动本地 AI 服务…";
                var ollamaLog=Path.Combine(data,"logs","ollama.log");
                var ollamaProcess=StartChild(ollama, "serve", root, ollamaLog);
                await WaitHttp($"http://127.0.0.1:{OllamaPort}/api/version", TimeSpan.FromSeconds(30),
                    "本地 AI 服务", ollamaProcess, ollamaLog);
                if (!await HasModel()) {
                    status.Text="首次运行：正在下载视觉与文案模型（约 2GB，可断点续传）…";
                    progress.Value=0;
                    var modelProgress=new Progress<int>(value=>{progress.Value=Math.Clamp(value,0,100);status.Text=$"首次运行：正在下载视觉与文案模型… {value}%";});
                    await PullModel(modelProgress, data);
                    File.WriteAllText(Path.Combine(data,"setup-complete.json"), JsonSerializer.Serialize(new { model=Model, completedAt=DateTimeOffset.Now }));
                }
                status.Text="正在启动 GameNarrator…";
                progress.Style=ProgressBarStyle.Marquee;
                }
                var java=Path.Combine(root,"runtime","bin","java.exe");
                var jar=Directory.GetFiles(Path.Combine(root,"app"),"*.jar").Single();
                var applicationLog=Path.Combine(data,"logs","application-console.log");
                var applicationProcess=StartChild(java, $"-Xms64m -Xmx{javaHeapMb}m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8 -jar \"{jar}\" --spring.profiles.active=release", root, applicationLog);
                await WaitHttp($"http://127.0.0.1:{AppPort}/api/debug/health", TimeSpan.FromSeconds(90),
                    "GameNarrator 后端", applicationProcess, applicationLog);
                DesktopLog("BACKEND_READY");
                await OpenDesktopAsync(data);
                DesktopLog("DESKTOP_READY");
                tray.ShowBalloonTip(2500,"GameNarrator 正在运行","桌面应用已启动，可通过右下角托盘图标重新打开。",ToolTipIcon.Info);
            } catch(Exception ex) {
                DesktopLog($"STARTUP_FAILED type={ex.GetType().Name} message={ex.Message}");
                progress.Visible=false; retry.Visible=true; status.Text="启动失败："+ex.Message+"\n日志位于安装目录的 data\\logs";
            }
        }

        private async Task OpenDesktopAsync(string data) {
            status.Text="正在打开桌面工作台…";
            webViewEnvironment=await CoreWebView2Environment.CreateAsync(userDataFolder:Path.Combine(data,"webview2"));
            await webView.EnsureCoreWebView2Async(webViewEnvironment);
            webView.CoreWebView2.Settings.AreDevToolsEnabled=false;
            webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled=false;
            webView.CoreWebView2.NewWindowRequested += (_,e) => {
                e.Handled=true;
                Process.Start(new ProcessStartInfo(e.Uri){UseShellExecute=true});
            };
            webView.CoreWebView2.NavigationStarting += (_,e) => {
                if (IsLocalApplicationUri(e.Uri)) return;
                e.Cancel=true;
                Process.Start(new ProcessStartInfo(e.Uri){UseShellExecute=true});
            };
            webView.CoreWebView2.WebMessageReceived += async (_,e) => await HandleWebMessageAsync(e);
            webView.Source=new Uri($"http://127.0.0.1:{AppPort}/?surface=desktop");
            webView.Visible=true; status.Visible=false; progress.Visible=false; retry.Visible=false;
            FormBorderStyle=FormBorderStyle.Sizable; MaximizeBox=true; WindowState=FormWindowState.Maximized;
            ShowDesktopWindow();
        }

        private async Task HandleWebMessageAsync(CoreWebView2WebMessageReceivedEventArgs e) {
            string? requestId=null;
            try {
                if (!IsLocalApplicationUri(webView.Source?.ToString() ?? "")) return;
                using var message=JsonDocument.Parse(e.WebMessageAsJson);
                var root=message.RootElement;
                if (!root.TryGetProperty("type",out var type)) return;
                requestId=root.GetProperty("requestId").GetString();
                if (type.GetString()=="bilibiliAssets") {
                    var assetRequestMode=root.TryGetProperty("mode",out var assetMode) ? assetMode.GetString() : "RECOMMEND";
                    var query=root.TryGetProperty("query",out var assetQuery) ? assetQuery.GetString() : null;
                    var page=root.TryGetProperty("page",out var assetPage) ? assetPage.GetInt32() : 1;
                    if (string.IsNullOrWhiteSpace(requestId)) return;
                    var items=await ExtractBilibiliAssetsAsync(assetRequestMode ?? "RECOMMEND",query,page);
                    PostAssetsResponse(requestId,items,null);
                    DesktopLog($"BILIBILI_ASSETS_SUCCESS mode={assetRequestMode} count={items.GetArrayLength()}");
                    return;
                }
                if (type.GetString()=="bilibiliArticle") {
                    var sourceUrl=root.TryGetProperty("sourceUrl",out var articleUrl) ? articleUrl.GetString() : null;
                    if (string.IsNullOrWhiteSpace(requestId) || string.IsNullOrWhiteSpace(sourceUrl)) return;
                    var article=await ExtractBilibiliArticleAsync(sourceUrl);
                    PostArticleResponse(requestId,article,null);
                    DesktopLog("BILIBILI_ARTICLE_EXTRACT_SUCCESS");
                    return;
                }
                if (type.GetString()!="bilibiliLogin") return;
                var mode=root.TryGetProperty("mode",out var selectedMode) ? selectedMode.GetString() : "QR";
                if (string.IsNullOrWhiteSpace(requestId)) return;
                if (bilibiliLoginRunning) throw new InvalidOperationException("Bilibili 登录窗口已经打开");
                bilibiliLoginRunning=true;
                DesktopLog($"BILIBILI_LOGIN_REQUEST mode={mode}");
                var token=await LoginBilibiliAsync(mode ?? "QR");
                PostLoginResponse(requestId,token,null);
                DesktopLog("BILIBILI_LOGIN_SUCCESS");
            } catch(Exception ex) {
                DesktopLog($"BILIBILI_LOGIN_FAILED type={ex.GetType().Name} message={ex.Message}");
                if (!string.IsNullOrWhiteSpace(requestId)) {
                    using var failed=JsonDocument.Parse(e.WebMessageAsJson);
                    if (failed.RootElement.GetProperty("type").GetString()=="bilibiliArticle")
                        PostArticleResponse(requestId,null,ex.Message);
                    else if (failed.RootElement.GetProperty("type").GetString()=="bilibiliAssets")
                        PostAssetsResponse(requestId,null,ex.Message);
                    else PostLoginResponse(requestId,null,ex.Message);
                }
            } finally {
                bilibiliLoginRunning=false;
            }
        }

        private async Task<string> LoginBilibiliAsync(string mode) {
            if (webViewEnvironment==null) throw new InvalidOperationException("桌面浏览器尚未准备完成");
            var existing=await webView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.bilibili.com/");
            if (HasBilibiliSession(existing)) return await UploadBilibiliCookiesAsync(existing);
            using var loginView=new WebView2 { Dock=DockStyle.Fill };
            using var loginWindow=new Form {
                Text=mode=="ACCOUNT" ? "Bilibili 官方账号登录" : "Bilibili 官方扫码登录",
                Width=1100,Height=780,StartPosition=FormStartPosition.CenterParent,MinimizeBox=false
            };
            loginWindow.Controls.Add(loginView);
            await loginView.EnsureCoreWebView2Async(webViewEnvironment);
            loginView.CoreWebView2.Settings.AreDevToolsEnabled=false;
            loginView.CoreWebView2.NavigationCompleted += async (_,_) => {
                var account=mode=="ACCOUNT" ? "true" : "false";
                await loginView.CoreWebView2.ExecuteScriptAsync("""
                    (() => {
                      const click = pattern => [...document.querySelectorAll('button,a,div,span')]
                        .find(item => pattern.test((item.textContent || '').trim()))?.click();
                      click(/^登录$/);
                      if (ACCOUNT_MODE) setTimeout(() => click(/密码登录|账号登录/), 800);
                    })();
                    """.Replace("ACCOUNT_MODE",account));
            };
            loginView.Source=new Uri("https://www.bilibili.com/");
            loginWindow.Show(this);
            DesktopLog("BILIBILI_LOGIN_WINDOW_OPENED");
            var deadline=DateTime.UtcNow.AddMinutes(3);
            while (!loginWindow.IsDisposed && loginWindow.Visible && DateTime.UtcNow<deadline) {
                var cookies=await loginView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.bilibili.com/");
                if (HasBilibiliSession(cookies)) {
                    loginWindow.Close();
                    return await UploadBilibiliCookiesAsync(cookies);
                }
                await Task.Delay(1000);
            }
            if (!loginWindow.IsDisposed) loginWindow.Close();
            throw new InvalidOperationException(DateTime.UtcNow>=deadline
                ? "等待 Bilibili 登录超时，请重试"
                : "已取消 Bilibili 登录");
        }

        private static bool HasBilibiliSession(IReadOnlyList<CoreWebView2Cookie> cookies) {
            var names=cookies.Select(cookie=>cookie.Name).ToHashSet(StringComparer.Ordinal);
            return names.Contains("SESSDATA") && names.Contains("DedeUserID");
        }

        private static async Task<string> UploadBilibiliCookiesAsync(IReadOnlyList<CoreWebView2Cookie> cookies) {
            var lines=new List<string>{"# Netscape HTTP Cookie File"};
            foreach(var cookie in cookies) {
                var expires=cookie.IsSession ? 0 : new DateTimeOffset(cookie.Expires.ToUniversalTime()).ToUnixTimeSeconds();
                lines.Add(string.Join('\t',cookie.Domain,cookie.Domain.StartsWith('.') ? "TRUE" : "FALSE",
                    string.IsNullOrEmpty(cookie.Path) ? "/" : cookie.Path,cookie.IsSecure ? "TRUE" : "FALSE",
                    expires,cookie.Name,cookie.Value));
            }
            using var client=new HttpClient{Timeout=TimeSpan.FromSeconds(30)};
            using var form=new MultipartFormDataContent();
            var file=new ByteArrayContent(Encoding.UTF8.GetBytes(string.Join('\n',lines)+"\n"));
            file.Headers.ContentType=new System.Net.Http.Headers.MediaTypeHeaderValue("text/plain");
            form.Add(file,"file","bilibili-cookies.txt");
            form.Add(new StringContent("https://www.bilibili.com/"),"url");
            using var response=await client.PostAsync($"http://127.0.0.1:{AppPort}/api/media-import/cookies",form);
            using var body=JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            if (!response.IsSuccessStatusCode) throw new InvalidOperationException(
                body.RootElement.TryGetProperty("message",out var error) ? error.GetString() : "Bilibili 会话导入失败");
            return body.RootElement.GetProperty("token").GetString()
                ?? throw new InvalidOperationException("Bilibili 会话导入未返回令牌");
        }

        private void PostLoginResponse(string requestId,string? token,string? error) {
            if (webView.CoreWebView2==null) return;
            webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new {
                type="bilibiliLoginResponse",requestId,token,error
            }));
        }

        private async Task<JsonElement> ExtractBilibiliArticleAsync(string sourceUrl) {
            if (webViewEnvironment==null) throw new InvalidOperationException("桌面浏览器尚未准备完成");
            if (!Uri.TryCreate(sourceUrl,UriKind.Absolute,out var uri) || uri.Scheme!="https"
                || !uri.Host.EndsWith("bilibili.com",StringComparison.OrdinalIgnoreCase)
                || !(uri.AbsolutePath.StartsWith("/read/cv") || uri.AbsolutePath.StartsWith("/opus/")))
                throw new InvalidOperationException("Bilibili 专栏地址无效");
            var cookies=await webView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.bilibili.com/");
            if (!HasBilibiliSession(cookies)) throw new InvalidOperationException("请先登录 Bilibili，再提取专栏图文");
            using var articleView=new WebView2 { Dock=DockStyle.Fill };
            using var articleWindow=new Form {Text="Bilibili 专栏提取",Width=1100,Height=780,
                StartPosition=FormStartPosition.CenterParent,MinimizeBox=false};
            articleWindow.Controls.Add(articleView);
            await articleView.EnsureCoreWebView2Async(webViewEnvironment);
            var loaded=new TaskCompletionSource<bool>();
            articleView.CoreWebView2.NavigationCompleted += (_,args) => loaded.TrySetResult(args.IsSuccess);
            articleView.Source=uri; articleWindow.Show(this);
            if (!await loaded.Task.WaitAsync(TimeSpan.FromSeconds(30)))
                throw new InvalidOperationException("Bilibili 专栏页面加载失败");
            JsonElement article=default;
            for (var attempt=0;attempt<12;attempt++) {
                var raw=await articleView.CoreWebView2.ExecuteScriptAsync("""
                    (() => {
                      const clean=v=>String(v||'').replace(/\s+/g,' ').trim();
                      const root=document.querySelector('#article-content,.article-content,.opus-module-content,[class*=\"article-content\"],article')||document.body;
                      const title=clean(document.querySelector('h1,.title,[class*=\"title\"]')?.textContent||document.title.replace(/_哔哩哔哩.*$/,''));
                      const author=clean(document.querySelector('.up-name,.author-name,[class*=\"author\"]')?.textContent);
                      const text=[...root.querySelectorAll('p,h2,h3,blockquote,li')].map(x=>clean(x.textContent)).filter(Boolean).join('\n').slice(0,50000);
                      const images=[...new Set([...root.querySelectorAll('img')].map(x=>x.currentSrc||x.src||x.dataset.src||'').map(x=>x.startsWith('//')?'https:'+x:x.replace(/^http:/,'https:')).filter(x=>/^https:\/\//.test(x)&&!/face|avatar|logo/i.test(x)))].slice(0,50);
                      return {title,author,text,images};
                    })()
                    """);
                article=JsonSerializer.Deserialize<JsonElement>(raw);
                if ((article.TryGetProperty("text",out var text) && text.GetString()?.Length>20)
                    || (article.TryGetProperty("images",out var images) && images.GetArrayLength()>0)) break;
                await Task.Delay(1000);
            }
            articleWindow.Close();
            return article;
        }

        private void PostArticleResponse(string requestId,JsonElement? article,string? error) {
            if (webView.CoreWebView2==null) return;
            webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new {
                type="bilibiliArticleResponse",requestId,article,error
            }));
        }

        private async Task<JsonElement> ExtractBilibiliAssetsAsync(string mode,string? query,int page) {
            if (webViewEnvironment==null) throw new InvalidOperationException("桌面浏览器尚未准备完成");
            var cookies=await webView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.bilibili.com/");
            if (!HasBilibiliSession(cookies)) throw new InvalidOperationException("请先登录 Bilibili，登录成功后会自动载入首页推荐");
            var target=mode=="SEARCH"
                ? $"https://search.bilibili.com/all?keyword={Uri.EscapeDataString(query ?? "")}&page={Math.Clamp(page,1,50)}"
                : "https://www.bilibili.com/";
            if (mode=="SEARCH" && string.IsNullOrWhiteSpace(query)) throw new InvalidOperationException("Bilibili 搜索词不能为空");
            using var sourceView=new WebView2 {Dock=DockStyle.Fill};
            using var sourceWindow=new Form {Width=2,Height=2,ShowInTaskbar=false,FormBorderStyle=FormBorderStyle.None,
                StartPosition=FormStartPosition.Manual,Location=new Point(-32000,-32000)};
            sourceWindow.Controls.Add(sourceView);
            await sourceView.EnsureCoreWebView2Async(webViewEnvironment);
            var loaded=new TaskCompletionSource<bool>();
            sourceView.CoreWebView2.NavigationCompleted += (_,args) => loaded.TrySetResult(args.IsSuccess);
            sourceView.Source=new Uri(target);
            sourceWindow.Show(this);
            if (!await loaded.Task.WaitAsync(TimeSpan.FromSeconds(30))) throw new InvalidOperationException("Bilibili 页面加载失败");
            for (var attempt=0;attempt<10;attempt++) {
                var raw=await sourceView.CoreWebView2.ExecuteScriptAsync("""
                    (()=>{const clean=v=>String(v||'').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim();const found=new Map();
                    for(const a of document.querySelectorAll('a[href*="/video/BV"]')){const m=a.href.match(/bilibili\.com\/video\/(BV[0-9A-Za-z]+)/i);if(!m||found.has(m[1]))continue;
                    const c=a.closest('.bili-video-card,.feed-card,[class*="video-card"],[class*="feed-card"]')||a.parentElement;
                    const n=c?.querySelector('.bili-video-card__info--tit a,.bili-video-card__info--tit,.video-name,h3 a,h3,a[title][href*="/video/BV"]');
                    const title=[a.getAttribute('title'),a.getAttribute('aria-label'),n?.getAttribute?.('title'),n?.textContent].map(clean).find(v=>v&&v.length>=2&&v.length<=200&&!/稍后再看/.test(v));if(!title)continue;
                    const img=c?.querySelector('img');let preview=img?.currentSrc||img?.src||img?.dataset?.src||'';if(preview.startsWith('//'))preview='https:'+preview;preview=preview.replace(/^http:/,'https:');
                    const creator=clean(c?.querySelector('[class*="author"],[class*="up-name"],[class*="owner"]')?.textContent);
                    found.set(m[1],{bvid:m[1],sourceUrl:'https://www.bilibili.com/video/'+m[1],title,creator,previewUrl:preview.startsWith('https://')?preview:'',metricsText:''});if(found.size>=30)break;}return [...found.values()];})()
                    """);
                var items=JsonSerializer.Deserialize<JsonElement>(raw);
                if (items.ValueKind==JsonValueKind.Array && items.GetArrayLength()>0) return items;
                await Task.Delay(1000);
            }
            throw new InvalidOperationException("没有读取到 Bilibili 内容，请确认账号首页或搜索页可正常访问");
        }

        private void PostAssetsResponse(string requestId,JsonElement? items,string? error) {
            if (webView.CoreWebView2==null) return;
            webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new {
                type="bilibiliAssetsResponse",requestId,items,error
            }));
        }

        internal void ShowDesktopWindow() {
            if (WindowState==FormWindowState.Minimized) WindowState=FormWindowState.Normal;
            Show(); Activate(); BringToFront();
        }

        private static bool IsLocalApplicationUri(string value) {
            return Uri.TryCreate(value,UriKind.Absolute,out var uri) &&
                (uri.Host=="127.0.0.1" || uri.Host.Equals("localhost",StringComparison.OrdinalIgnoreCase)) &&
                uri.Port==AppPort;
        }

        protected override void OnFormClosing(FormClosingEventArgs e) { tray.Visible=false; tray.Dispose(); StopChildren(); base.OnFormClosing(e); }
    }

    private static Process StartChild(string file, string args, string cwd, string log) {
        if(!File.Exists(file)) throw new FileNotFoundException("发行组件缺失",file);
        var stream=new FileStream(log,FileMode.Append,FileAccess.Write,FileShare.ReadWrite); var writer=new StreamWriter(stream,Encoding.UTF8){AutoFlush=true};
        var p=new Process { StartInfo=new ProcessStartInfo(file,args){WorkingDirectory=cwd,UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true} };
        p.OutputDataReceived+=(_,e)=>{if(e.Data!=null)writer.WriteLine(e.Data);}; p.ErrorDataReceived+=(_,e)=>{if(e.Data!=null)writer.WriteLine(e.Data);};
        p.EnableRaisingEvents=true; p.Exited+=(_,_)=>writer.Dispose(); p.Start(); p.BeginOutputReadLine(); p.BeginErrorReadLine(); Children.Add(p); return p;
    }

    private static async Task<bool> HasModel() {
        using var client=new HttpClient{Timeout=TimeSpan.FromSeconds(10)};
        var json=await client.GetFromJsonAsync<JsonElement>($"http://127.0.0.1:{OllamaPort}/api/tags");
        return json.GetProperty("models").EnumerateArray().Any(x => x.GetProperty("name").GetString()?.StartsWith(Model,StringComparison.OrdinalIgnoreCase)==true);
    }

    private static async Task PullModel(IProgress<int> progress,string data) {
        Exception? last=null;
        for(var attempt=1;attempt<=3;attempt++) {
            try { await PullModelOnce(progress,data); return; }
            catch(Exception ex) {
                last=ex; DesktopLog($"OLLAMA_PULL_RETRY attempt={attempt} reason={ex.Message}");
                if(attempt<3) await Task.Delay(TimeSpan.FromSeconds(attempt*3));
            }
        }
        throw new InvalidOperationException("模型下载连续失败 3 次；已保留 Ollama 的分片缓存，重试会继续下载。最后错误："+last?.Message,last);
    }

    private static async Task PullModelOnce(IProgress<int> progress,string data) {
        using var totalTimeout=new CancellationTokenSource(TimeSpan.FromHours(3));
        using var client=new HttpClient{Timeout=Timeout.InfiniteTimeSpan};
        using var request=new HttpRequestMessage(HttpMethod.Post,$"http://127.0.0.1:{OllamaPort}/api/pull") {
            Content=JsonContent.Create(new {name=Model,stream=true})
        };
        using var response=await client.SendAsync(request,HttpCompletionOption.ResponseHeadersRead,totalTimeout.Token);
        response.EnsureSuccessStatusCode();
        await using var stream=await response.Content.ReadAsStreamAsync(totalTimeout.Token);
        using var reader=new StreamReader(stream);
        await using var log=new StreamWriter(Path.Combine(data,"logs","model-install.log"),true,Encoding.UTF8){AutoFlush=true};
        while(true) {
            string? line;
            try { line=await reader.ReadLineAsync(totalTimeout.Token).AsTask().WaitAsync(TimeSpan.FromMinutes(5),totalTimeout.Token); }
            catch(TimeoutException ex) { throw new TimeoutException("模型下载连续 5 分钟没有收到进度，可能是网络中断",ex); }
            if(line==null) break;
            await log.WriteLineAsync(line);
            using var json=JsonDocument.Parse(line); var root=json.RootElement;
            if(root.TryGetProperty("error",out var error)) throw new InvalidOperationException(error.GetString());
            if(root.TryGetProperty("completed",out var completed)&&root.TryGetProperty("total",out var total)&&total.GetInt64()>0)
                progress.Report((int)Math.Clamp(completed.GetInt64()*100/total.GetInt64(),0,100));
        }
        progress.Report(100);
    }

    private static async Task<string> EnsureOllama(string data,IProgress<int> progress) {
        var toolDir=Path.Combine(data,"tools","ollama"); var exe=Path.Combine(toolDir,"ollama.exe");
        if(File.Exists(exe)) return exe;
        Directory.CreateDirectory(toolDir); var archive=Path.Combine(data,"downloads","ollama-windows-amd64-v0.32.5.zip");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!); var existing=File.Exists(archive)?new FileInfo(archive).Length:0;
        using var client=new HttpClient{Timeout=TimeSpan.FromHours(3)};
        using var request=new HttpRequestMessage(HttpMethod.Get,OllamaUrl);
        if(existing>0) request.Headers.Range=new System.Net.Http.Headers.RangeHeaderValue(existing,null);
        using var response=await client.SendAsync(request,HttpCompletionOption.ResponseHeadersRead);
        response.EnsureSuccessStatusCode();
        var append=response.StatusCode==System.Net.HttpStatusCode.PartialContent && existing>0;
        var remaining=response.Content.Headers.ContentLength; var total=remaining.HasValue?existing+remaining.Value:0L; var received=existing;
        await using(var input=await response.Content.ReadAsStreamAsync())
        await using(var output=new FileStream(archive,append?FileMode.Append:FileMode.Create,FileAccess.Write,FileShare.Read)) {
            var buffer=new byte[1024*1024]; int count;
            while((count=await input.ReadAsync(buffer))>0) {
                await output.WriteAsync(buffer.AsMemory(0,count)); received+=count;
                if(total>0) progress.Report((int)Math.Clamp(received*100/total,0,100));
            }
        }
        await using(var stream=File.OpenRead(archive)) {
            var hash=Convert.ToHexString(await SHA256.HashDataAsync(stream)).ToLowerInvariant();
            if(hash!=OllamaSha256) throw new InvalidDataException("Ollama 下载校验失败，请删除下载缓存后重试");
        }
        ZipFile.ExtractToDirectory(archive,toolDir,true);
        if(!File.Exists(exe)) throw new InvalidDataException("Ollama 安装包内容不完整");
        return exe;
    }

    private static async Task WaitHttp(string url,TimeSpan timeout,string service,Process process,string logPath) {
        using var client=new HttpClient{Timeout=TimeSpan.FromSeconds(3)}; var until=DateTime.UtcNow+timeout;
        while(DateTime.UtcNow<until){
            if(process.HasExited) throw new InvalidOperationException($"{service}启动后立即退出（退出码 {process.ExitCode}）。日志：{TailLog(logPath)}");
            try{using var response=await client.GetAsync(url);if(response.IsSuccessStatusCode)return;}catch{} await Task.Delay(700);
        }
        var port=new Uri(url).Port;
        throw new TimeoutException($"{service}在 {timeout.TotalSeconds:0} 秒内未就绪（端口 {port}）。可能原因：端口被其他程序抢占、进程卡死或运行组件损坏。日志：{TailLog(logPath)}");
    }
    private static string TailLog(string path) {
        try {
            if(!File.Exists(path)) return "尚未生成日志";
            using var stream=new FileStream(path,FileMode.Open,FileAccess.Read,FileShare.ReadWrite);
            var length=(int)Math.Min(2000,stream.Length); stream.Seek(-length,SeekOrigin.End);
            using var reader=new StreamReader(stream,Encoding.UTF8,true); return reader.ReadToEnd().ReplaceLineEndings(" ").Trim();
        } catch(Exception ex) { return "无法读取日志："+ex.Message; }
    }
    private static int FindFreePort(int preferred,params int[] excluded) {
        for(var port=preferred;port<preferred+100;port++) {
            if(excluded.Contains(port)) continue;
            try { var listener=new TcpListener(IPAddress.Loopback,port); listener.Start(); listener.Stop(); return port; } catch(SocketException) { }
        }
        var fallback=new TcpListener(IPAddress.Loopback,0); fallback.Start(); var selected=((IPEndPoint)fallback.LocalEndpoint).Port; fallback.Stop(); return selected;
    }
    private static bool WantsLocalAi(string data) {
        var file=Path.Combine(data,"config","ai-settings.json");
        if(!File.Exists(file)) return false;
        try {
            using var json=JsonDocument.Parse(File.ReadAllText(file));
            var localMode=json.RootElement.TryGetProperty("mode",out var mode) &&
                string.Equals(mode.GetString(),"LOCAL",StringComparison.OrdinalIgnoreCase);
            var deepSeek=json.RootElement.TryGetProperty("provider",out var provider) &&
                string.Equals(provider.GetString(),"DEEPSEEK",StringComparison.OrdinalIgnoreCase);
            return localMode || deepSeek;
        } catch { return false; }
    }
    private static void StopChildren(){foreach(var p in Children.AsEnumerable().Reverse()){try{if(!p.HasExited)p.Kill(true);}catch{} try{p.Dispose();}catch{}}Children.Clear();}
    private static void DesktopLog(string message) {
        if (string.IsNullOrWhiteSpace(DesktopLogPath)) return;
        try {
            var safe=System.Text.RegularExpressions.Regex.Replace(message,
                "(?i)(api[-_ ]?key|authorization|cookie|password|secret|token)\\s*[=:]\\s*[^\\s,;}]+","$1=***");
            File.AppendAllText(DesktopLogPath,$"{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss.fff} {safe}{Environment.NewLine}",Encoding.UTF8);
        } catch { }
    }
}
