package cn.longer233.gamenarrator.mobile;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@UnstableApi
public final class ProjectTaskDialogController {
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController ui;
    private final DialogController.ProjectHost projectHost;
    private final DialogController.CompilationHost compilationHost;
    private final DialogController.PlatformImportHost platformImportHost;
    private final DialogController.ShotSearchHost shotSearchHost;
    private DialogController.AssetLibraryHost assetLibraryHost;
    private DialogController.PipelineHost pipelineHost;
    private DialogController.HomeHost homeHost;
    private DialogController.RevisionHost revisionHost;

    public ProjectTaskDialogController(Context context, DialogController.ProjectHost projectHost,
                                       DialogController.CompilationHost compilationHost,
                                       DialogController.PlatformImportHost platformImportHost,
                                       DialogController.ShotSearchHost shotSearchHost,
                                       DialogController ui) {
        this.context = context;
        this.projectHost = projectHost;
        this.compilationHost = compilationHost;
        this.platformImportHost = platformImportHost;
        this.shotSearchHost = shotSearchHost;
        this.ui = ui;
    }

    public void attachAssetLibraryHost(DialogController.AssetLibraryHost host) { this.assetLibraryHost = host; }
    public void attachPipelineHost(DialogController.PipelineHost host) { this.pipelineHost = host; }
    public void attachHomeHost(DialogController.HomeHost host) { this.homeHost = host; }
    public void attachRevisionHost(DialogController.RevisionHost host) { this.revisionHost = host; }

    public void showProjectActions(ProjectRepository.ProjectInfo project) {
        if (projectHost == null) return;
        String[] actions = {"重命名项目", "复制项目", "移入回收站"};
        new AlertDialog.Builder(context).setTitle(project.name()).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                EditText input = new EditText(context);
                input.setText(project.name());
                new AlertDialog.Builder(context).setTitle("项目名称").setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("保存", (x, w) -> {
                            try {
                                projectHost.renameProject(project.id(), input.getText().toString());
                            } catch (Exception error) {
                                projectHost.showErrorDialog("无法重命名项目", error.getMessage());
                            }
                        }).show();
            } else if (which == 1) {
                try {
                    projectHost.duplicateProject(project.id());
                } catch (Exception error) {
                    projectHost.showErrorDialog("无法复制项目", error.getMessage());
                }
            } else {
                if (project.id() == projectHost.activeProjectId()
                        && projectHost.exportActiveForProject(project.id())) {
                    projectHost.unavailable("当前项目正在导出，不能移入回收站。");
                    return;
                }
                new AlertDialog.Builder(context).setTitle("将项目移入回收站？")
                        .setMessage("项目、版本和编辑参数会保留，可在设置中恢复。不会删除手机原媒体或已导出视频。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("移入回收站", (x, w) -> projectHost.archiveProject(project.id())).show();
            }
        }).setNegativeButton("关闭", null).show();
    }

    public void showRevisionActions(ProjectRepository.RevisionInfo revision) {
        if (projectHost == null) return;
        String[] actions = {"重命名版本", "从此版本建立项目分支", "检出并覆盖当前时间线"};
        new AlertDialog.Builder(context).setTitle(revision.reason()).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                EditText input = new EditText(context);
                input.setText(revision.reason());
                new AlertDialog.Builder(context).setTitle("版本名称").setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("保存", (x, w) -> {
                            try {
                                projectHost.renameRevision(revision.id(), input.getText().toString());
                            } catch (Exception error) {
                                projectHost.showErrorDialog("无法重命名版本", error.getMessage());
                            }
                        }).show();
            } else if (which == 1) {
                EditText input = new EditText(context);
                input.setText(projectHost.activeProjectName() + " · " + revision.reason());
                new AlertDialog.Builder(context).setTitle("新项目分支名称").setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("建立分支", (x, w) -> {
                            try {
                                projectHost.forkRevision(revision.id(), input.getText().toString());
                            } catch (Exception error) {
                                projectHost.showErrorDialog("无法建立版本分支", error.getMessage());
                            }
                        }).show();
            } else {
                projectHost.restoreRevision(revision);
            }
        }).setNegativeButton("关闭", null).show();
    }

    public void createCompilation(TimelineClip pending) {
        if (compilationHost == null) return;
        EditText input = new EditText(context);
        input.setHint("合集名称");
        new AlertDialog.Builder(context).setTitle("新建切片合集").setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isBlank()) name = "未命名合集";
                    long id = compilationHost.createCompilation(name);
                    if (pending != null) {
                        compilationHost.addCompilationItem(id, pending.key());
                        compilationHost.setStatus("片段已加入新合集。");
                    } else {
                        compilationHost.refreshCompilationsPage();
                    }
                }).show();
    }

    public void showAddToCompilation(TimelineClip clip) {
        if (compilationHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("加入切片合集");
        panel.addView(ui.action("＋ 新建合集", Color.rgb(255, 79, 163), TEXT,
                v -> {
                    dialog.dismiss();
                    createCompilation(clip);
                }), ui.match(ui.dp(52)));
        for (ProjectRepository.CompilationInfo value : compilationHost.listCompilations()) {
            Button add = ui.action(value.name() + " · " + value.itemCount() + " 个片段", Color.WHITE, TEXT, v -> {
                compilationHost.addCompilationItem(value.id(), clip.key());
                dialog.dismiss();
                compilationHost.setStatus("片段已加入“" + value.name() + "”。");
            });
            add.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams p = ui.match(ui.dp(54));
            p.setMargins(0, ui.dp(7), 0, 0);
            panel.addView(add, p);
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showCompilations() {
        if (compilationHost == null) return;
        ViewGroup container = compilationHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> compilationHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("切片合集", 24, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        head.addView(ui.action("＋ 新建", Color.rgb(255, 79, 163), TEXT, v -> createCompilation(null)),
                new LinearLayout.LayoutParams(ui.dp(96), ui.dp(46)));
        root.addView(head);
        root.addView(ui.label("跨项目收集片段、保存播放顺序，再生成独立剪辑项目。", 13, MUTED, false));
        List<ProjectRepository.CompilationInfo> values = compilationHost.listCompilations();
        if (values.isEmpty()) {
            root.addView(ui.label("暂无合集。可在任意项目中选中片段后加入合集。", 14, MUTED, false), ui.match(ui.dp(100)));
        }
        for (ProjectRepository.CompilationInfo value : values) {
            Button item = ui.action(value.name() + "\n" + value.itemCount() + " 个片段", SURFACE_HIGH, TEXT,
                    v -> compilationHost.openCompilationPage(value));
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams p = ui.match(ui.dp(68));
            p.setMargins(0, ui.dp(8), 0, 0);
            root.addView(item, p);
        }
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void openCompilation(ProjectRepository.CompilationInfo compilation) {
        if (compilationHost == null) return;
        ViewGroup container = compilationHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.addView(ui.iconButton("‹", v -> compilationHost.showCompilationsPage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label(compilation.name(), 22, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(head);
        List<ProjectRepository.CompilationItemInfo> items = compilationHost.listCompilationItems(compilation.id());
        for (int i = 0; i < items.size(); i++) {
            ProjectRepository.CompilationItemInfo item = items.get(i);
            LinearLayout card = ui.card(i % 2 == 0 ? SURFACE_HIGH : Color.WHITE);
            card.addView(ui.label((i + 1) + ". " + item.clipName(), 16, TEXT, true));
            card.addView(ui.label(item.projectName() + " · " + compilationHost.formatDuration(item.clip().durationMs()),
                    12, MUTED, false));
            LinearLayout actions = ui.row();
            actions.addView(ui.action("↑", Color.rgb(54, 201, 255), TEXT,
                    v -> {
                        compilationHost.moveCompilationItem(compilation.id(), item.id(), -1);
                        compilationHost.openCompilationPage(compilation);
                    }), new LinearLayout.LayoutParams(ui.dp(52), ui.dp(42)));
            actions.addView(ui.action("↓", Color.rgb(255, 229, 72), TEXT,
                    v -> {
                        compilationHost.moveCompilationItem(compilation.id(), item.id(), 1);
                        compilationHost.openCompilationPage(compilation);
                    }), new LinearLayout.LayoutParams(ui.dp(52), ui.dp(42)));
            actions.addView(ui.action("移除", Color.rgb(255, 216, 234), TEXT,
                    v -> {
                        compilationHost.removeCompilationItem(item.id());
                        compilationHost.openCompilationPage(compilation);
                    }), new LinearLayout.LayoutParams(ui.dp(72), ui.dp(42)));
            card.addView(actions);
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(7), 0, ui.dp(7));
            root.addView(card, p);
        }
        root.addView(ui.action("生成本地剪辑项目", Color.rgb(255, 79, 163), TEXT,
                v -> compilationHost.materializeCompilation(compilation, items)), ui.match(ui.dp(56)));
        root.addView(ui.action("删除合集", DANGER, TEXT,
                v -> ui.confirm("删除合集？", "只删除合集和排序，不删除原项目片段。", "删除",
                        () -> {
                            compilationHost.deleteCompilation(compilation.id());
                            compilationHost.showCompilationsPage();
                        })), ui.match(ui.dp(52)));
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void showPlatformImport() {
        if (platformImportHost == null) return;
        ViewGroup container = platformImportHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> platformImportHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("平台导入", 24, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(head);
        LinearLayout note = ui.card(SURFACE_HIGH);
        note.addView(ui.label("手机端直链导入", 18, TEXT, true));
        note.addView(ui.label("支持无需登录的 HTTP/HTTPS 媒体直链，也会解析常见网页中的 og:video、video/source 直链并按格式筛选；需要登录的网页与 Cookie 合规登录仍不伪装为可用。", 13, TEXT, false));
        root.addView(note);
        LinearLayout biliRow = ui.row();
        biliRow.addView(ui.action("Bilibili 登录助手", Color.rgb(54, 201, 255), TEXT, v -> showBilibiliLogin()),
                new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        biliRow.addView(ui.action("提取专栏图文", Color.rgb(255, 229, 72), TEXT, v -> showArticleExtract()),
                new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(biliRow, ui.match(ui.dp(52)));
        EditText url = ui.creativeInput("粘贴媒体地址（https://…）", "");
        url.setSingleLine(false);
        root.addView(url, ui.match(ui.dp(92)));
        CheckBox createProject = new CheckBox(context);
        createProject.setText("视频下载后建立新的剪辑项目");
        createProject.setTextColor(TEXT);
        createProject.setChecked(true);
        root.addView(createProject, ui.match(ui.dp(46)));
        Spinner format = new Spinner(context);
        format.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"自动选择", "MP4", "MOV", "WEBM", "MKV", "音频"}));
        root.addView(format, ui.match(ui.dp(52)));
        root.addView(ui.action("解析并下载到手机", Color.rgb(255, 79, 163), TEXT,
                v -> platformImportHost.startRemoteImport(url.getText().toString(),
                        new String[]{"AUTO", "MP4", "MOV", "WEBM", "MKV", "AUDIO"}[format.getSelectedItemPosition()],
                        createProject.isChecked())), ui.match(ui.dp(56)));
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void showBilibiliLogin() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("Bilibili 登录助手");
        panel.addView(ui.label("登录在 B 站官方页面完成；GameNarrator 不接触或保存账号密码，Cookie 只存在于内存，关闭后立即清除。", 13, MUTED, false));
        final PlatformLoginSession session = new PlatformLoginSession();
        TextView sessionStatus = ui.label("尚未登录：点“本机确认”会直接拉起已安装的 B 站 App 完成登录；也可使用 cookies.txt。", 13, MUTED, false);
        panel.addView(ui.action("本机确认（B 站 App）", Color.rgb(54, 201, 255), TEXT, v -> {
            sessionStatus.setText("已拉起本机 B 站 App，请在 App 内确认登录；成功后自动完成。");
            new BilibiliQrLogin(context, session).startDeviceConfirm(new BilibiliQrLogin.LoginListener() {
                @Override public void onSuccess(String cookies) {
                    sessionStatus.setText("已登录：本次会话可直接解析与下载，Cookie 仅存内存，关闭面板即清除。");
                    Toast.makeText(context, "Bilibili 登录成功，本次会话可直接导入", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                }
                @Override public void onError(String message) {
                    sessionStatus.setText("登录失败或已取消：" + message);
                }
            });
        }), ui.match(ui.dp(52)));
        panel.addView(ui.action("打开官方扫码登录", Color.rgb(54, 201, 255), TEXT,
                v -> session.showLogin(context, () -> {
                    sessionStatus.setText("已登录：本次会话可直接解析与下载，Cookie 仅存内存，关闭面板即清除。");
                    Toast.makeText(context, "Bilibili 登录成功，本次会话可直接导入", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                }, null)), ui.match(ui.dp(52)));
        panel.addView(ui.action("选择 cookies.txt（临时）", Color.rgb(255, 229, 72), TEXT,
                v -> platformImportHost.launchCookieFileOpen()), ui.match(ui.dp(52)));
        panel.addView(sessionStatus);
        panel.addView(ui.action("关闭并清除会话", DANGER, TEXT, v -> {
            session.clearSession();
            dialog.dismiss();
        }), ui.match(ui.dp(52)));
        panel.addView(ui.label("受限内容需要登录时，提取会携带本次内存会话，不会写入数据库、日志或归档。", 12, MUTED, false));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showArticleExtract() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("提取 Bilibili 专栏 / Opus");
        EditText url = ui.creativeInput("粘贴 read/cv... 或 opus/... 链接", "");
        TextView message = ui.label("提取结果只显示在页面上，不会自动入库。", 12, MUTED, false);
        LinearLayout result = ui.column();
        LinearLayout loginBox = ui.column();
        PlatformLoginSession session = new PlatformLoginSession();
        final WebView[] loginView = new WebView[1];
        panel.addView(ui.action("B 站官方登录（保留在当前面板）", Color.rgb(54, 201, 255), TEXT, v -> {
            if (loginView[0] == null) {
                WebView view = session.createWebView(context);
                view.setLayoutParams(ui.match(ui.dp(420)));
                loginBox.addView(view);
                loginView[0] = view;
                view.loadUrl(PlatformLoginSession.LOGIN_URL);
                message.setText("请在页面内完成登录；Cookie 仅存内存，关闭本面板即清除。");
            } else {
                message.setText("登录页面已打开，完成登录后即可提取。");
            }
        }), ui.match(ui.dp(50)));
        panel.addView(loginBox);
        final Button[] goRef = new Button[1];
        Button go = ui.action("提取", Color.rgb(255, 229, 72), TEXT, v -> {
            String link = url.getText().toString().trim();
            if (link.isBlank()) {
                message.setText("请输入链接。");
                return;
            }
            message.setText("正在提取…");
            result.removeAllViews();
            goRef[0].setEnabled(false);
            new Thread(() -> {
                try {
                    String webCookie = session.cookiesFor("https://www.bilibili.com");
                    String fileCookie = platformImportHost.cookieSessionFor("https://www.bilibili.com");
                    String cookie = combineCookies(webCookie, fileCookie);
                    BilibiliArticle article = BilibiliArticleExtractor.extract(link, cookie);
                    ui.post(() -> {
                        goRef[0].setEnabled(true);
                        message.setText("提取完成。");
                        LinearLayout card = ui.card(Color.WHITE);
                        card.addView(ui.label(article.title().isBlank() ? "（无标题）" : article.title(), 15, TEXT, true));
                        card.addView(ui.label(article.content().isBlank() ? "（无正文）" : article.content(), 12, MUTED, false));
                        LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
                        p.setMargins(0, ui.dp(6), 0, ui.dp(6));
                        result.addView(card, p);
                        for (String image : article.imageUrls()) {
                            result.addView(ui.action("打开图片链接", Color.WHITE, TEXT, b -> openUrl(image)),
                                    ui.match(ui.dp(46)));
                        }
                    });
                } catch (Exception error) {
                    ui.post(() -> {
                        goRef[0].setEnabled(true);
                        message.setText("提取失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
                    });
                }
            }).start();
        });
        goRef[0] = go;
        panel.addView(url);
        panel.addView(go, ui.match(ui.dp(52)));
        panel.addView(message);
        panel.addView(result);
        panel.addView(ui.action("关闭", DANGER, TEXT, v -> dialog.dismiss()), ui.match(ui.dp(50)));
        dialog.setOnDismissListener(d -> session.clearSession());
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private static String combineCookies(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append("; ");
            out.append(part);
        }
        return out.toString();
    }

    public void showShotSearch(String query) {
        if (shotSearchHost == null) return;
        ViewGroup container = shotSearchHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> shotSearchHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("镜头文本搜索", 24, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(head);
        LinearLayout note = ui.card(SURFACE_HIGH);
        note.addView(ui.label("端侧文本检索", 16, TEXT, true));
        boolean visionReady = MobileModelDirectory.check(context).vision();
        boolean semanticReady = TextEmbedding.isReady(context);
        note.addView(ui.label((semanticReady
                ? "端侧语义检索已就绪：本地 bge-small-zh embedding 按语义匹配名称、字幕、解说和特效提示。"
                : "当前为端侧关键词检索（分词与词频）；语义向量检索需另装 embedding 模型，未安装时不会宣称语义能力。")
                + (visionReady ? " 图像相似度检索（MobileNet）可用。" : " 图像相似度需要 vision-*.onnx，当前未安装。"),
                13, MUTED, false));
        root.addView(note);
        List<TimelineClip> projectClips = shotSearchHost.projectClips();
        if (visionReady && !projectClips.isEmpty()) {
            LinearLayout imageCard = ui.card(Color.WHITE);
            imageCard.addView(ui.label("图像相似度", 16, TEXT, true));
            TextView imageStatus = ui.label("为当前项目镜头建立 MobileNet 特征索引，并用第一镜画面搜索相似镜头。",
                    12, MUTED, false);
            imageCard.addView(ui.action("构建图像索引并搜索", Color.rgb(54, 201, 255), TEXT, v -> {
                imageStatus.setText("正在提取画面特征…");
                new Thread(() -> {
                    try {
                        ImageSearchIndex index = new ImageSearchIndex();
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        String queryKey = null;
                        float[] queryVector = null;
                        List<String> orderedKeys = new ArrayList<>();
                        for (TimelineClip clip : projectClips) {
                            retriever.setDataSource(context, clip.uri());
                            String durationText = retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_DURATION);
                            long duration = durationText == null ? 0 : Long.parseLong(durationText);
                            Bitmap frame = retriever.getFrameAtTime(duration / 2 * 1000,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                            if (frame == null) continue;
                            float[] vector = MobileNetVisionClassifier.embed(context, frame);
                            index.add(clip.key(), vector);
                            orderedKeys.add(clip.key());
                            if (queryKey == null) {
                                queryKey = clip.key();
                                queryVector = vector;
                            }
                        }
                        retriever.release();
                        StringBuilder result = new StringBuilder("相似镜头：");
                        for (String key : index.search(queryVector, 5)) {
                            int idx = orderedKeys.indexOf(key);
                            if (result.length() > 0) result.append("、");
                            result.append("第 ").append(idx + 1).append(" 镜");
                        }
                        String text = result.toString();
                        ui.post(() -> imageStatus.setText(text));
                    } catch (Exception error) {
                        String message = error.getMessage() == null ? "未知错误" : error.getMessage();
                        ui.post(() -> imageStatus.setText("图像检索失败：" + message));
                    }
                }).start();
            }), ui.match(ui.dp(50)));
            imageCard.addView(imageStatus);
            root.addView(imageCard);
        }
        LinearLayout searchRow = ui.row();
        EditText input = ui.creativeInput("搜索字幕、解说、特效或文件名", "");
        input.setSingleLine(true);
        searchRow.addView(input, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        searchRow.addView(ui.action("搜索", Color.rgb(54, 201, 255), TEXT,
                v -> showShotSearch(input.getText().toString())), new LinearLayout.LayoutParams(ui.dp(76), ui.dp(52)));
        root.addView(searchRow, ui.match(ui.dp(56)));
        if (projectClips.isEmpty()) {
            root.addView(ui.label("当前项目还没有片段。", 14, MUTED, false), ui.match(ui.dp(80)));
        } else {
            List<ShotTextIndex.Segment> segments = new ArrayList<>();
            for (TimelineClip clip : projectClips) {
                segments.add(new ShotTextIndex.Segment(clip.key(), clip.name(), clip.subtitle(), clip.narration(), clip.effectCue()));
            }
            List<ShotTextIndex.Hit> hits = semanticReady
                    ? semanticShotSearch(segments, query)
                    : ShotTextIndex.search(segments, query, 50);
            if (query == null || query.trim().isBlank()) {
                root.addView(ui.label("输入关键词后按“搜索”。", 13, MUTED, false), ui.match(ui.dp(60)));
            } else if (hits.isEmpty()) {
                root.addView(ui.label("没有找到匹配镜头。", 14, MUTED, false), ui.match(ui.dp(60)));
            }
            for (ShotTextIndex.Hit hit : hits) {
                int index = -1;
                for (int i = 0; i < projectClips.size(); i++) {
                    if (projectClips.get(i).key().equals(hit.key())) { index = i; break; }
                }
                if (index < 0) continue;
                TimelineClip clip = projectClips.get(index);
                String snippet = clip.subtitle().isBlank() ? clip.narration() : clip.subtitle();
                if (snippet.isBlank()) snippet = clip.name();
                if (snippet.length() > 80) snippet = snippet.substring(0, 80) + "…";
                LinearLayout card = ui.card(index % 2 == 0 ? SURFACE_HIGH : Color.WHITE);
                card.addView(ui.label("第 " + (index + 1) + " 镜 · " + clip.name(), 15, TEXT, true));
                card.addView(ui.label("匹配：" + hit.fields() + " · 相关度 " + hit.score(), 12, MUTED, false));
                card.addView(ui.label(snippet, 13, TEXT, false));
                LinearLayout actions = ui.row();
                actions.addView(ui.action("定位分镜", Color.rgb(54, 201, 255), TEXT,
                        v -> shotSearchHost.openShot(clip)), new LinearLayout.LayoutParams(0, ui.dp(44), 1));
                actions.addView(ui.action("导出片段", Color.rgb(255, 79, 163), TEXT,
                        v -> shotSearchHost.exportShot(clip)), new LinearLayout.LayoutParams(0, ui.dp(44), 1));
                card.addView(actions);
                LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
                p.setMargins(0, ui.dp(7), 0, ui.dp(7));
                root.addView(card, p);
            }
        }
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    private List<ShotTextIndex.Hit> semanticShotSearch(List<ShotTextIndex.Segment> segments, String query) {
        try {
            float[] queryVector = TextEmbedding.encode(context, query);
            List<float[]> vectors = new ArrayList<>();
            for (ShotTextIndex.Segment segment : segments) {
                String text = segment.name() + " " + segment.subtitle() + " " + segment.narration()
                        + " " + segment.effectCue();
                vectors.add(TextEmbedding.encode(context, text));
            }
            List<Map.Entry<ShotTextIndex.Hit, Float>> scored = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                float score = cosine(queryVector, vectors.get(i));
                if (score > 0.35f) {
                    scored.add(new java.util.AbstractMap.SimpleEntry<>(
                            new ShotTextIndex.Hit(segments.get(i).key(), Math.round(score * 1000), "语义"),
                            score));
                }
            }
            scored.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
            List<ShotTextIndex.Hit> hits = new ArrayList<>();
            for (int i = 0; i < Math.min(50, scored.size()); i++) hits.add(scored.get(i).getKey());
            return hits;
        } catch (Exception error) {
            return ShotTextIndex.search(segments, query, 50);
        }
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0;
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) dot += a[i] * b[i];
        return dot;
    }

    public void showAssetLibrary(String query) {
        if (assetLibraryHost == null) return;
        ViewGroup container = assetLibraryHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> assetLibraryHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("素材库", 24, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        head.addView(ui.action("＋ 导入", Color.rgb(255, 79, 163), TEXT,
                v -> assetLibraryHost.launchAssetPicker()), new LinearLayout.LayoutParams(ui.dp(96), ui.dp(46)));
        root.addView(head);
        root.addView(ui.label("本地图片、视频和音频；移除目录记录不会删除手机原文件。", 13, MUTED, false));
        LinearLayout searchRow = ui.row();
        EditText search = ui.creativeInput("搜索名称、类型或标签", query);
        search.setSingleLine(true);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        searchRow.addView(ui.action("搜索", Color.rgb(54, 201, 255), TEXT,
                v -> assetLibraryHost.refreshAssetLibrary(search.getText().toString())), new LinearLayout.LayoutParams(ui.dp(76), ui.dp(52)));
        root.addView(searchRow, ui.match(ui.dp(56)));
        LinearLayout publicRow = ui.row();
        publicRow.addView(ui.action("公共素材搜索", Color.rgb(54, 201, 255), TEXT, v -> showPublicAssetSearch()),
                new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        publicRow.addView(ui.action("素材站导航", Color.rgb(255, 229, 72), TEXT, v -> showSourceDirectory()),
                new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        root.addView(publicRow, ui.match(ui.dp(50)));
        LinearLayout recommendRow = ui.row();
        recommendRow.addView(ui.action("按当前任务推荐素材", Color.rgb(113, 230, 108), TEXT,
                v -> showRecommendedAssets()), new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        root.addView(recommendRow, ui.match(ui.dp(50)));
        List<MobileAssetStore.AssetInfo> assets = assetLibraryHost.listAssets();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.CHINA);
        int visibleAssets = 0;
        for (MobileAssetStore.AssetInfo asset : assets) {
            String searchable = (asset.name() + " " + asset.type() + " " + asset.tags()).toLowerCase(Locale.CHINA);
            if (!normalized.isBlank() && !searchable.contains(normalized)) continue;
            visibleAssets++;
            LinearLayout card = ui.card(asset.type().startsWith("audio/") ? SURFACE_HIGH : Color.WHITE);
            card.setOrientation(LinearLayout.HORIZONTAL);
            ImageView preview = new ImageView(context);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackgroundColor(TEXT);
            card.addView(preview, new LinearLayout.LayoutParams(ui.dp(92), ui.dp(68)));
            assetLibraryHost.loadAssetPreview(asset, preview);
            LinearLayout copy = ui.column();
            copy.setPadding(ui.dp(12), 0, 0, 0);
            copy.addView(ui.label(asset.name(), 15, TEXT, true));
            copy.addView(ui.label(asset.type() + " · " + (asset.tags().isBlank() ? "无标签" : asset.tags()), 11, MUTED, false));
            LinearLayout actions = ui.row();
            actions.addView(ui.action("预览", Color.rgb(54, 201, 255), TEXT,
                    v -> assetLibraryHost.previewAsset(asset)), new LinearLayout.LayoutParams(ui.dp(68), ui.dp(40)));
            actions.addView(ui.action("标签", Color.rgb(255, 229, 72), TEXT,
                    v -> assetLibraryHost.editAssetTags(asset)), new LinearLayout.LayoutParams(ui.dp(68), ui.dp(40)));
            actions.addView(ui.action("移除", Color.rgb(255, 216, 234), TEXT,
                    v -> assetLibraryHost.confirmRemoveAsset(asset)), new LinearLayout.LayoutParams(ui.dp(68), ui.dp(40)));
            copy.addView(actions);
            card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(8), 0, ui.dp(8));
            root.addView(card, p);
        }
        if (visibleAssets == 0) {
            root.addView(ui.label(assets.isEmpty() ? "暂无素材。点击“导入”从手机选择。" : "没有匹配的本地素材。",
                    15, MUTED, false), ui.match(ui.dp(100)));
        }
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void showPublicAssetSearch() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("公共素材搜索");
        panel.addView(ui.label("匿名检索 Wikimedia Commons 与 Openverse；下载前必须逐项确认许可与再创作权利。", 13, MUTED, false));
        EditText query = ui.creativeInput("搜索词", "");
        Spinner provider = new Spinner(context);
        provider.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Wikimedia Commons", "Openverse"}));
        Spinner type = new Spinner(context);
        type.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"图片", "音频"}));
        TextView message = ui.label("输入搜索词后开始。", 12, MUTED, false);
        LinearLayout results = ui.column();
        final Button[] searchRef = new Button[1];
        Button search = ui.action("搜索公共素材", Color.rgb(54, 201, 255), TEXT, v -> {
            String q = query.getText().toString().trim();
            if (q.isBlank()) {
                message.setText("请输入搜索词。");
                return;
            }
            message.setText("正在搜索…");
            results.removeAllViews();
            searchRef[0].setEnabled(false);
            boolean wikimedia = provider.getSelectedItemPosition() == 0;
            boolean audio = type.getSelectedItemPosition() == 1;
            new Thread(() -> {
                try {
                    List<PublicAsset> assets = wikimedia ? PublicAssetSearch.searchWikimedia(q, 12)
                            : PublicAssetSearch.searchOpenverse(q, audio ? "audio" : "image", 12);
                    ui.post(() -> {
                        searchRef[0].setEnabled(true);
                        if (assets.isEmpty()) {
                            message.setText("没有找到结果。");
                            return;
                        }
                        message.setText("找到 " + assets.size() + " 个结果。");
                        for (PublicAsset asset : assets) {
                            LinearLayout card = ui.card(Color.WHITE);
                            card.addView(ui.label(asset.title(), 14, TEXT, true));
                            card.addView(ui.label((asset.creator().isBlank() ? "未知作者" : asset.creator())
                                    + " · " + asset.license() + " · " + asset.mediaType(), 11, MUTED, false));
                            LinearLayout actions = ui.row();
                            if (!asset.pageUrl().isBlank()) {
                                actions.addView(ui.action("来源页", Color.rgb(54, 201, 255), TEXT,
                                        b -> openUrl(asset.pageUrl())), new LinearLayout.LayoutParams(0, ui.dp(40), 1));
                            }
                            if (!asset.directUrl().isBlank()) {
                                actions.addView(ui.action("复制直链", Color.rgb(255, 229, 72), TEXT,
                                        b -> copyText(asset.directUrl())), new LinearLayout.LayoutParams(0, ui.dp(40), 1));
                            }
                            card.addView(actions);
                            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
                            p.setMargins(0, ui.dp(6), 0, ui.dp(6));
                            results.addView(card, p);
                        }
                    });
                } catch (Exception error) {
                    ui.post(() -> {
                        searchRef[0].setEnabled(true);
                        message.setText("搜索失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
                    });
                }
            }).start();
        });
        searchRef[0] = search;
        panel.addView(query);
        panel.addView(provider, ui.match(ui.dp(48)));
        panel.addView(type, ui.match(ui.dp(48)));
        panel.addView(search, ui.match(ui.dp(52)));
        panel.addView(message);
        panel.addView(results);
        panel.addView(ui.action("关闭", DANGER, TEXT, v -> dialog.dismiss()), ui.match(ui.dp(50)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showRecommendedAssets() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("按当前任务推荐素材");
        String keyword = assetLibraryHost == null ? "" : assetLibraryHost.activeProjectName();
        final String searchKeyword = (keyword == null || keyword.isBlank()) ? "游戏解说" : keyword;
        panel.addView(ui.label("根据当前任务“" + searchKeyword + "”检索开放素材（Wikimedia Commons / Openverse）；下载前必须逐项确认许可与再创作权利。", 13, MUTED, false));
        TextView message = ui.label("正在检索…", 12, MUTED, false);
        LinearLayout results = ui.column();
        panel.addView(message);
        panel.addView(results);
        panel.addView(ui.action("关闭", DANGER, TEXT, v -> dialog.dismiss()), ui.match(ui.dp(50)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
        new Thread(() -> {
            try {
                List<PublicAsset> images = PublicAssetSearch.searchWikimedia(searchKeyword, 6);
                List<PublicAsset> audio = PublicAssetSearch.searchOpenverse(searchKeyword, "audio", 6);
                ui.post(() -> {
                    message.setText("图片 " + images.size() + " 条 · 音频 " + audio.size() + " 条，下载前请确认权利。");
                    addRecommendSection(results, "图片推荐", images);
                    addRecommendSection(results, "音频推荐", audio);
                });
            } catch (Exception error) {
                ui.post(() -> message.setText(
                        "推荐失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage())));
            }
        }).start();
    }

    private void addRecommendSection(LinearLayout container, String title, List<PublicAsset> assets) {
        container.addView(ui.label(title, 15, TEXT, true));
        if (assets == null || assets.isEmpty()) {
            container.addView(ui.label("没有找到可用结果。", 12, MUTED, false));
            return;
        }
        for (PublicAsset asset : assets) {
            LinearLayout card = ui.card(Color.WHITE);
            card.addView(ui.label(asset.title(), 14, TEXT, true));
            card.addView(ui.label((asset.creator().isBlank() ? "未知作者" : asset.creator())
                    + " · " + asset.license() + " · " + asset.mediaType(), 11, MUTED, false));
            LinearLayout actions = ui.row();
            if (!asset.pageUrl().isBlank()) {
                actions.addView(ui.action("来源页", Color.rgb(54, 201, 255), TEXT,
                        b -> openUrl(asset.pageUrl())), new LinearLayout.LayoutParams(0, ui.dp(40), 1));
            }
            if (!asset.directUrl().isBlank()) {
                actions.addView(ui.action("复制直链", Color.rgb(255, 229, 72), TEXT,
                        b -> copyText(asset.directUrl())), new LinearLayout.LayoutParams(0, ui.dp(40), 1));
            }
            card.addView(actions);
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(6), 0, ui.dp(6));
            container.addView(card, p);
        }
    }

    public void showSourceDirectory() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("素材站导航");
        panel.addView(ui.label("国内来源优先；下载前必须逐项确认会员、署名、商用和修改权限。", 13, MUTED, false));
        String[][] sites = {
                {"Bilibili", "https://www.bilibili.com"},
                {"抖音", "https://www.douyin.com"},
                {"Pexels", "https://www.pexels.com"},
                {"Pixabay", "https://pixabay.com"},
                {"Openverse", "https://openverse.org"},
                {"Wikimedia Commons", "https://commons.wikimedia.org"},
                {"Freesound", "https://freesound.org"}
        };
        for (String[] site : sites) {
            panel.addView(ui.action(site[0] + " ↗", Color.WHITE, TEXT, v -> openUrl(site[1])), ui.match(ui.dp(48)));
        }
        panel.addView(ui.action("关闭", DANGER, TEXT, v -> dialog.dismiss()), ui.match(ui.dp(50)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private void openUrl(String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            ui.showError("无法打开链接", error.getMessage());
        }
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("素材直链", text));
        Toast.makeText(context, "已复制直链", Toast.LENGTH_SHORT).show();
    }

    public void showPipeline() {
        if (pipelineHost == null) return;
        ViewGroup container = pipelineHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> pipelineHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("剪辑任务 · 阶段", 24, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(head);
        root.addView(ui.label("每个项目按“导入 → 修剪 → 分镜 → 配音/素材 → 版本 → 渲染”推进；阶段状态持久化，重启后可继续。", 13, MUTED, false));
        String activeStage = pipelineHost.pipelineStage();
        if (activeStage != null) {
            LinearLayout nextCard = ui.card(SURFACE_HIGH);
            nextCard.addView(ui.label("下一步", 16, TEXT, true));
            if ("RENDER".equals(activeStage) && pipelineHost.exportActive()) {
                nextCard.addView(ui.action("取消导出", DANGER, TEXT, v -> pipelineHost.cancelActiveExport()),
                        ui.match(ui.dp(50)));
            } else if ("IMPORT".equals(activeStage)) {
                nextCard.addView(ui.action("导入素材（当前阶段）", Color.rgb(255, 79, 163), TEXT,
                        v -> pipelineHost.launchVideoPicker()), ui.match(ui.dp(50)));
            } else if ("RENDER".equals(activeStage) && "COMPLETED".equals(pipelineHost.activeProjectStatus())) {
                nextCard.addView(ui.action("重新导出", Color.rgb(255, 79, 163), TEXT,
                        v -> pipelineHost.startProjectExport()), ui.match(ui.dp(50)));
            } else if ("RENDER".equals(activeStage)) {
                nextCard.addView(ui.action("开始导出（当前阶段）", Color.rgb(255, 79, 163), TEXT,
                        v -> pipelineHost.startProjectExport()), ui.match(ui.dp(50)));
            } else {
                nextCard.addView(ui.action("完成当前阶段并进入下一步", Color.rgb(255, 229, 72), TEXT, v -> {
                    pipelineHost.completeCurrentStage();
                    showPipeline();
                }), ui.match(ui.dp(50)));
            }
            int activeIndex = PipelineStages.index(activeStage);
            if (pipelineHost.autoPipelineActive()) {
                nextCard.addView(ui.action("取消自动流水线", DANGER, TEXT,
                        v -> pipelineHost.cancelAutoPipeline()), ui.match(ui.dp(48)));
            } else if (activeIndex >= PipelineStages.index(PipelineStages.STORYBOARD)
                    && activeIndex < PipelineStages.index(PipelineStages.RENDER)) {
                nextCard.addView(ui.action("一键自动继续（批量解说 + 自动导出）", Color.rgb(255, 229, 72), TEXT,
                        v -> pipelineHost.runAutomaticPipeline()), ui.match(ui.dp(48)));
            }
            LinearLayout.LayoutParams nextParams = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            nextParams.setMargins(0, ui.dp(10), 0, ui.dp(10));
            root.addView(nextCard, nextParams);
            LinearLayout enhance = ui.card(Color.WHITE);
            enhance.addView(ui.label("智能增强工具箱", 16, TEXT, true));
            enhance.addView(ui.action("自动匹配素材（按任务推荐）", Color.rgb(54, 201, 255), TEXT,
                    v -> showRecommendedAssets()), ui.match(ui.dp(46)));
            enhance.addView(ui.action("自动规划特效并渲染", Color.rgb(255, 79, 163), TEXT,
                    v -> pipelineHost.autoPlanEffectsAndRender()), ui.match(ui.dp(46)));
            LinearLayout.LayoutParams enhanceParams = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            enhanceParams.setMargins(0, ui.dp(10), 0, ui.dp(10));
            root.addView(enhance, enhanceParams);
        }
        List<ProjectRepository.ProjectInfo> projects = pipelineHost.listProjects();
        if (projects.isEmpty()) {
            root.addView(ui.label("还没有项目。", 14, MUTED, false), ui.match(ui.dp(80)));
        }
        for (ProjectRepository.ProjectInfo project : projects) {
            String stage = pipelineHost.pipelineStageFor(project.id());
            LinearLayout card = ui.card("COMPLETED".equals(project.status()) ? Color.rgb(113, 230, 108) : SURFACE_HIGH);
            card.addView(ui.label(project.name(), 16, TEXT, true));
            card.addView(ui.label(pipelineHost.statusLabel(project.status()) + " · 当前阶段 " + PipelineStages.label(stage)
                    + " · " + project.clipCount() + " 个片段", 12, MUTED, false));
            LinearLayout stages = ui.row();
            for (String value : PipelineStages.order()) {
                TextView dot = ui.label(PipelineStages.isCompleted(value, stage) ? "●" : value.equals(stage) ? "◉" : "○",
                        14, value.equals(stage) ? Color.rgb(255, 79, 163) : MUTED, true);
                stages.addView(dot);
            }
            card.addView(stages);
            card.setOnClickListener(v -> pipelineHost.openProject(project.id()));
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(7), 0, ui.dp(7));
            root.addView(card, p);
        }
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void showPipelinePanel() {
        if (pipelineHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("项目流水线阶段");
        String stage = pipelineHost.pipelineStage();
        for (int i = 0; i < PipelineStages.order().length; i++) {
            String value = PipelineStages.order()[i];
            String mark = PipelineStages.isCompleted(value, stage) ? "✓ " : value.equals(stage) ? "▶ " : "";
            int color = PipelineStages.isCompleted(value, stage) ? Color.rgb(113, 230, 108)
                    : value.equals(stage) ? Color.rgb(255, 229, 72) : Color.WHITE;
            panel.addView(ui.label(mark + PipelineStages.label(value) + (value.equals(stage) ? "（当前）" : ""),
                    15, color, true), ui.match(ui.dp(40)));
        }
        if ("RENDER".equals(stage) && pipelineHost.exportActive()) {
            panel.addView(ui.action("取消导出", DANGER, TEXT, v -> {
                pipelineHost.cancelActiveExport();
                dialog.dismiss();
            }), ui.match(ui.dp(50)));
        } else if ("IMPORT".equals(stage)) {
            panel.addView(ui.action("导入素材（当前阶段）", Color.rgb(255, 79, 163), TEXT, v -> {
                dialog.dismiss();
                pipelineHost.launchVideoPicker();
            }), ui.match(ui.dp(50)));
        } else if ("RENDER".equals(stage) && "COMPLETED".equals(pipelineHost.activeProjectStatus())) {
            panel.addView(ui.action("重新导出", Color.rgb(255, 79, 163), TEXT, v -> {
                dialog.dismiss();
                pipelineHost.startProjectExport();
            }), ui.match(ui.dp(50)));
        } else if ("RENDER".equals(stage)) {
            panel.addView(ui.action("开始导出（当前阶段）", Color.rgb(255, 79, 163), TEXT, v -> {
                dialog.dismiss();
                pipelineHost.startProjectExport();
            }), ui.match(ui.dp(50)));
        } else {
            panel.addView(ui.action("标记当前阶段完成并进入下一步", Color.rgb(255, 229, 72), TEXT, v -> {
                pipelineHost.completeCurrentStage();
                dialog.dismiss();
                showPipelinePanel();
            }), ui.match(ui.dp(50)));
        }
        int stageIndex = PipelineStages.index(stage);
        if (pipelineHost.autoPipelineActive()) {
            panel.addView(ui.action("取消自动流水线", DANGER, TEXT, v -> {
                pipelineHost.cancelAutoPipeline();
                dialog.dismiss();
            }), ui.match(ui.dp(50)));
        } else if (stageIndex >= PipelineStages.index(PipelineStages.STORYBOARD)
                && stageIndex < PipelineStages.index(PipelineStages.RENDER)) {
            panel.addView(ui.action("一键自动继续（批量解说 + 自动导出）", Color.rgb(255, 229, 72), TEXT, v -> {
                dialog.dismiss();
                pipelineHost.runAutomaticPipeline();
            }), ui.match(ui.dp(50)));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showHome() {
        if (homeHost == null) return;
        ViewGroup container = homeHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(32));
        LinearLayout brand = ui.row();
        brand.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        brand.setBackground(ui.outlined(Color.WHITE, 0));
        brand.setElevation(ui.dp(6));
        ImageView mark = new ImageView(context);
        mark.setImageResource(cn.longer233.gamenarrator.mobile.R.drawable.ic_brand_mark);
        brand.addView(mark, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout brandCopy = ui.column();
        brandCopy.setPadding(ui.dp(12), 0, 0, 0);
        brandCopy.addView(ui.label("GAME·NARRATOR  v" + homeHost.versionName(), 20, TEXT, true));
        brandCopy.addView(ui.label("本地推理工作台", 12, MUTED, true));
        brand.addView(brandCopy);
        root.addView(brand);
        Space heroGap = new Space(context);
        root.addView(heroGap, new LinearLayout.LayoutParams(1, ui.dp(46)));
        LinearLayout nav = ui.row();
        nav.addView(ui.navButton("剪辑任务", Color.WHITE, v -> homeHost.showPipelinePage()),
                new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        nav.addView(ui.navButton("平台导入", Color.rgb(255, 79, 163), v -> homeHost.showPlatformImportPage()),
                new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        nav.addView(ui.navButton("素材库", Color.rgb(54, 201, 255), v -> homeHost.showAssetLibraryPage()),
                new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        nav.addView(ui.navButton("设置", Color.WHITE, v -> homeHost.showSettingsPage()),
                new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        LinearLayout.LayoutParams navParams = ui.match(ui.dp(58));
        navParams.setMargins(0, ui.dp(18), 0, ui.dp(20));
        root.addView(nav, navParams);
        LinearLayout hero = ui.card(Color.rgb(54, 201, 255));
        hero.addView(ui.label("MULTIMODAL GAME COMMENTARY", 11, TEXT, true));
        hero.addView(ui.label("把游戏录像变成\n完整动漫剧场式解说", 31, TEXT, true));
        hero.addView(ui.label("默认保留完整录像内容；端侧能力按本体流程逐步接入。", 14, TEXT, true));
        root.addView(hero);
        LinearLayout tools = ui.row();
        tools.addView(ui.action("镜头搜索", Color.rgb(54, 201, 255), TEXT,
                v -> homeHost.showShotSearchPage()), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        tools.addView(ui.action("切片合集", Color.rgb(255, 229, 72), TEXT,
                v -> homeHost.showCompilationsPage()), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        LinearLayout.LayoutParams toolsParams = ui.match(ui.dp(52));
        toolsParams.setMargins(0, ui.dp(12), 0, ui.dp(12));
        root.addView(tools, toolsParams);
        LinearLayout tools2 = ui.row();
        tools2.addView(ui.action("AI 设置", Color.rgb(54, 201, 255), TEXT,
                v -> homeHost.showAiSettingsPage()), new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        tools2.addView(ui.action("公共素材", Color.rgb(113, 230, 108), TEXT,
                v -> homeHost.showPublicAssetSearch()), new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        tools2.addView(ui.action("素材站导航", Color.rgb(255, 229, 72), TEXT,
                v -> homeHost.showSourceDirectory()), new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        LinearLayout.LayoutParams tools2Params = ui.match(ui.dp(50));
        tools2Params.setMargins(0, 0, 0, ui.dp(8));
        root.addView(tools2, tools2Params);
        LinearLayout tools3 = ui.row();
        tools3.addView(ui.action("使用引导", Color.WHITE, TEXT,
                v -> homeHost.showGuidePage()), new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        tools3.addView(ui.action("更新公告", Color.WHITE, TEXT,
                v -> homeHost.showReleaseNotesPage()), new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        LinearLayout.LayoutParams tools3Params = ui.match(ui.dp(50));
        tools3Params.setMargins(0, 0, 0, ui.dp(12));
        root.addView(tools3, tools3Params);
        Button create = ui.action("建立本地剪辑任务  →", Color.rgb(255, 79, 163), TEXT,
                v -> showCreateTaskForm());
        LinearLayout.LayoutParams createParams = ui.match(ui.dp(56));
        createParams.setMargins(0, ui.dp(28), 0, ui.dp(28));
        root.addView(create, createParams);
        root.addView(ui.sectionTitle("MISSION HISTORY · 本地任务"));
        List<ProjectRepository.ProjectInfo> projects = homeHost.listProjects();
        if (projects.isEmpty()) {
            LinearLayout recent = ui.card(Color.rgb(255, 216, 234));
            recent.addView(ui.label("还没有项目", 17, TEXT, true));
            recent.addView(ui.label("创建项目后，片段会出现在本地时间线中。", 14, MUTED, false));
            root.addView(recent);
        } else {
            for (ProjectRepository.ProjectInfo project : projects) {
                int color = "COMPLETED".equals(project.status()) ? Color.rgb(113, 230, 108)
                        : "FAILED".equals(project.status()) ? Color.rgb(255, 216, 234) : SURFACE_HIGH;
                LinearLayout recent = ui.card(color);
                recent.addView(ui.label(project.name(), 18, TEXT, true));
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                        .format(new java.util.Date(project.updatedAt()));
                String categoryLabel = "STORY".equals(project.gameCategory()) ? "剧情向"
                        : "RPG".equals(project.gameCategory()) ? "角色扮演"
                        : "ANIME_GAME".equals(project.gameCategory()) ? "二次元游戏" : "动作竞技";
                String scopeLabel = "HIGHLIGHTS".equals(project.editingScope()) ? "精彩片段" : "完整视频";
                recent.addView(ui.label(categoryLabel + " · " + scopeLabel + " · "
                        + homeHost.statusLabel(project.status()) + " · " + project.clipCount()
                        + " 个片段 · " + time, 13, MUTED, false));
                recent.setOnClickListener(v -> homeHost.openProject(project.id()));
                recent.setOnLongClickListener(v -> {
                    homeHost.showProjectActions(project);
                    return true;
                });
                LinearLayout.LayoutParams recentParams = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
                recentParams.setMargins(0, ui.dp(8), 0, ui.dp(8));
                root.addView(recent, recentParams);
            }
        }
        Space gap = new Space(context);
        root.addView(gap, new LinearLayout.LayoutParams(1, ui.dp(20)));
        LinearLayout privacy = ui.card(SURFACE_HIGH);
        privacy.addView(ui.label("LOCAL · 数据优先", 16, TEXT, true));
        privacy.addView(ui.label("无需登录 · 无需电脑 · 无后台上传", 13, TEXT, false));
        root.addView(privacy);
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void showCreateTaskForm() {
        if (homeHost == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText name = ui.creativeInput("任务名称（最多 120 字）", "");
        Spinner category = new Spinner(context);
        category.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"动作竞技", "剧情向", "角色扮演", "二次元游戏"}));
        Spinner style = new Spinner(context);
        style.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"动漫剧场", "热血高燃", "轻松吐槽"}));
        Spinner scope = new Spinner(context);
        scope.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"完整视频（默认）", "仅精彩片段"}));
        EditText duration = ui.number("目标时长（秒），仅精彩片段生效", 90);
        EditText brief = ui.creativeInput("创作要求（最多 500 字）", "");
        EditText glossary = ui.creativeInput("术语纠错词表（每行 错误词=正确词，最多 4000 字）", "");
        CheckBox reviewPause = new CheckBox(context);
        reviewPause.setText("AI 生成文案和分镜后暂停，等待检查和修改");
        reviewPause.setTextColor(TEXT);
        reviewPause.setChecked(true);
        CheckBox automatic = new CheckBox(context);
        automatic.setText("启动自动剪辑流程");
        automatic.setTextColor(TEXT);
        automatic.setChecked(true);
        panel.addView(ui.label("任务参数", 15, TEXT, true));
        panel.addView(name);
        panel.addView(ui.label("内容类别", 13, MUTED, true));
        panel.addView(category, ui.match(ui.dp(48)));
        panel.addView(ui.label("解说风格", 13, MUTED, true));
        panel.addView(style, ui.match(ui.dp(48)));
        panel.addView(ui.label("剪辑范围", 13, MUTED, true));
        panel.addView(scope, ui.match(ui.dp(48)));
        panel.addView(duration);
        panel.addView(brief, ui.match(ui.dp(92)));
        panel.addView(glossary, ui.match(ui.dp(92)));
        panel.addView(reviewPause, ui.match(ui.dp(44)));
        panel.addView(automatic, ui.match(ui.dp(44)));
        panel.addView(ui.label("端侧暂未提供云端视觉/文案/配音引擎时，对应环节保持空轨道或规则流程。", 12, MUTED, false));
        scroll.addView(panel);
        new AlertDialog.Builder(context).setTitle("建立本地剪辑任务")
                .setView(scroll).setNegativeButton("取消", null)
                .setPositiveButton("创建并选择视频", (dialog, which) -> {
                    String taskName = name.getText().toString().trim();
                    if (taskName.isBlank()) taskName = "未命名项目";
                    if (taskName.length() > 120) taskName = taskName.substring(0, 120);
                    int target = (int) Math.max(0, Math.min(3600, ui.longValue(duration)));
                    homeHost.createProjectWithBrief(taskName,
                            new String[]{"ACTION", "STORY", "RPG", "ANIME_GAME"}[category.getSelectedItemPosition()],
                            new String[]{"ANIME_THEATER", "PASSIONATE", "HUMOROUS"}[style.getSelectedItemPosition()],
                            new String[]{"FULL_VIDEO", "HIGHLIGHTS"}[scope.getSelectedItemPosition()],
                            target, brief.getText().toString().trim(), glossary.getText().toString().trim(),
                            reviewPause.isChecked(), automatic.isChecked());
                }).show();
    }

    public void openRevisionPanel() {
        if (revisionHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = ui.sheet("项目版本历史");
        content.addView(ui.label("重要编辑会自动生成快照，最多保留 100 个版本。点按检出，长按可重命名或建立项目分支。", 13, MUTED, false));
        content.addView(ui.action("版本对比 / 合并", Color.rgb(255, 229, 72), TEXT, v -> {
            dialog.dismiss();
            revisionHost.showRevisionComparePicker();
        }), ui.match(ui.dp(48)));
        List<ProjectRepository.RevisionInfo> revisions = revisionHost.listRevisions();
        if (revisions.isEmpty()) {
            content.addView(ui.label("尚无版本记录。完成一次编辑后会自动创建。", 14, MUTED, false));
        } else {
            for (ProjectRepository.RevisionInfo revision : revisions) {
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                        .format(new java.util.Date(revision.createdAt()));
                Button item = ui.action(time + "  ·  " + revision.reason() + "\n" + revision.clipCount() + " 个片段",
                        Color.WHITE, TEXT, v -> {
                            revisionHost.restoreRevision(revision);
                            dialog.dismiss();
                        });
                item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                item.setOnLongClickListener(v -> {
                    dialog.dismiss();
                    revisionHost.showRevisionActions(revision);
                    return true;
                });
                LinearLayout.LayoutParams itemParams = ui.match(ui.dp(68));
                itemParams.setMargins(0, ui.dp(8), 0, 0);
                content.addView(item, itemParams);
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showRevisionComparePicker() {
        if (revisionHost == null) return;
        List<ProjectRepository.RevisionInfo> revisions = revisionHost.listRevisions();
        if (revisions.isEmpty()) {
            revisionHost.unavailable("当前项目还没有历史版本。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("版本对比 / 合并");
        panel.addView(ui.label("选择历史版本与当前时间线对比，或把该版本合并进当前项目。", 13, MUTED, false));
        for (ProjectRepository.RevisionInfo revision : revisions) {
            String time = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                    .format(new java.util.Date(revision.createdAt()));
            LinearLayout card = ui.card(Color.WHITE);
            card.addView(ui.label(time + " · " + revision.reason(), 15, TEXT, true));
            card.addView(ui.label(revision.clipCount() + " 个片段", 12, MUTED, false));
            LinearLayout actions = ui.row();
            actions.addView(ui.action("对比", Color.rgb(54, 201, 255), TEXT,
                    v -> {
                        dialog.dismiss();
                        showRevisionDiff(revision);
                    }), new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            actions.addView(ui.action("合并", Color.rgb(255, 79, 163), TEXT,
                    v -> {
                        dialog.dismiss();
                        showRevisionMergeOptions(revision);
                    }), new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            card.addView(actions);
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(7), 0, ui.dp(7));
            panel.addView(card, p);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showRevisionDiff(ProjectRepository.RevisionInfo revision) {
        if (revisionHost == null) return;
        RevisionMerge.Diff diff = RevisionMerge.diff(revisionHost.currentClips(),
                revisionHost.loadRevisionClips(revision.id()));
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("版本差异");
        panel.addView(ui.label("当前 → 历史版本：新增 " + diff.added().size() + " · 移除 "
                + diff.removed().size() + " · 内容变化 " + diff.changed().size(), 16, TEXT, true));
        if (diff.added().isEmpty() && diff.removed().isEmpty() && diff.changed().isEmpty()) {
            panel.addView(ui.label("两个版本内容一致。", 14, MUTED, false));
        }
        addDiffGroup(panel, "历史版本新增", diff.added());
        addDiffGroup(panel, "当前独有", diff.removed());
        addDiffGroup(panel, "内容变化", diff.changed());
        panel.addView(ui.action("关闭", Color.rgb(255, 79, 163), TEXT, v -> dialog.dismiss()), ui.match(ui.dp(50)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
        revisionHost.logEvent("revision", "查看版本差异 " + revision.id());
    }

    private void addDiffGroup(LinearLayout panel, String title, List<RevisionMerge.ClipView> values) {
        if (values.isEmpty()) return;
        panel.addView(ui.label(title, 14, TEXT, true));
        for (RevisionMerge.ClipView value : values) {
            panel.addView(ui.label(value.name() + " · " + revisionHost.formatDuration(value.endMs() - value.startMs()),
                    12, MUTED, false), ui.match(ui.dp(38)));
        }
    }

    public void showRevisionMergeOptions(ProjectRepository.RevisionInfo revision) {
        if (revisionHost == null) return;
        new AlertDialog.Builder(context).setTitle("合并历史版本")
                .setMessage("只补缺失：保留当前时间线顺序，把历史版本独有的片段追加到末尾；覆盖同名会用历史版本内容替换同关键帧片段。")
                .setNegativeButton("取消", null)
                .setPositiveButton("只补缺失", (d, w) ->
                        revisionHost.applyRevisionMerge(revision, RevisionMerge.Mode.UNION))
                .setNeutralButton("覆盖同名片段", (d, w) ->
                        revisionHost.applyRevisionMerge(revision, RevisionMerge.Mode.OVERWRITE)).show();
    }
}
