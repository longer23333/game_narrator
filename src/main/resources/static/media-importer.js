(() => {
  const form = document.querySelector("#media-resolve-form");
  const result = document.querySelector("#media-resolve-result");
  const message = document.querySelector("#media-import-message");
  const bilibiliLoginState = document.querySelector("[data-bilibili-login-state]");
  if (!form || !result) return;
  let resolvedUrl = "";
  let cookieToken = "";
  let resolvedMedia = null;
  let autoDownloadRequested = false;
  let autoPreviewRequested = false;
  const authenticationPreferencesKey = 'gameNarrator.mediaAuthenticationPreferences';
  const mediaPreferencesKey = 'gameNarrator.mediaImportPreferences';
  const readJsonPreference = (key, fallback = {}) => {
    try { return JSON.parse(localStorage.getItem(key) || '') || fallback; }
    catch { return fallback; }
  };
  const sourcePlatform = sourceUrl => {
    try { return new URL(sourceUrl).hostname.toLowerCase().replace(/^www\./, ''); }
    catch { return ''; }
  };
  const remembersAuthentication = sourceUrl => Boolean(readJsonPreference(authenticationPreferencesKey)[sourcePlatform(sourceUrl)]);
  const rememberAuthentication = sourceUrl => {
    const platform = sourcePlatform(sourceUrl);
    if (!platform) return;
    localStorage.setItem(authenticationPreferencesKey, JSON.stringify({
      ...readJsonPreference(authenticationPreferencesKey), [platform]:true
    }));
  };
  const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
  const size = bytes => bytes ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : "大小未知";
  const libraryTags = () => {
    const tags = [...(resolvedMedia?.tags || [])];
    if (resolvedMedia?.contentOriginLabel) tags.push(`来源判断:${resolvedMedia.contentOriginLabel}`);
    return [...new Set(tags)].slice(0, 20);
  };
  async function request(url, body) {
    const response = await fetch(url, {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `请求失败（HTTP ${response.status}）`);
    return data;
  }
  async function read(url) {
    const response = await fetch(url);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `请求失败（HTTP ${response.status}）`);
    return data;
  }
  const wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
  async function uploadCookieFile(file, sourceUrl) {
    const body = new FormData();
    body.append("file", file);
    body.append("url", sourceUrl);
    const response = await fetch("/api/media-import/cookies", {method:"POST", body});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `Cookie 上传失败（HTTP ${response.status}）`);
    return data.token;
  }
  const isAuthenticationError = error => /(登录|认证|cookie|http 40[123]|sign in|login|no video formats found)/i.test(error.message || "");
  const resolveMedia = token => request("/api/media-import/resolve", {
    url:resolvedUrl, rightsConfirmed:true, cookieToken:token || null
  });
  function requestBrowserAuthentication(sourceUrl) {
    return new Promise((resolve, reject) => {
      const requestId = crypto.randomUUID();
      const timeout = setTimeout(() => {
        window.removeEventListener("gamenarrator-browser-auth-response", receive);
        reject(new Error("未检测到已连接的登录助手扩展"));
      }, 12000);
      function receive(event) {
        if (event.detail?.requestId !== requestId) return;
        clearTimeout(timeout);
        window.removeEventListener("gamenarrator-browser-auth-response", receive);
        if (event.detail.error) reject(new Error(event.detail.error));
        else resolve(event.detail.token);
      }
      window.addEventListener("gamenarrator-browser-auth-response", receive);
      window.dispatchEvent(new CustomEvent("gamenarrator-browser-auth-request", {
        detail:{requestId, sourceUrl}
      }));
    });
  }
  function requestBilibiliLogin(mode) {
    return new Promise((resolve, reject) => {
      const requestId = crypto.randomUUID();
      const desktopBridge = window.chrome?.webview;
      const timeout = setTimeout(() => {
        window.removeEventListener("gamenarrator-bilibili-login-response", receive);
        desktopBridge?.removeEventListener("message", receiveDesktop);
        reject(new Error("等待 Bilibili 登录超时，请重试"));
      }, 190000);
      function receive(event) {
        if (event.detail?.requestId !== requestId) return;
        clearTimeout(timeout);
        window.removeEventListener("gamenarrator-bilibili-login-response", receive);
        if (event.detail.error) reject(new Error(event.detail.error));
        else resolve(event.detail.token);
      }
      function receiveDesktop(event) {
        if (event.data?.type !== "bilibiliLoginResponse" || event.data?.requestId !== requestId) return;
        clearTimeout(timeout);
        desktopBridge.removeEventListener("message", receiveDesktop);
        if (event.data.error) reject(new Error(event.data.error));
        else resolve(event.data.token);
      }
      if (desktopBridge) {
        desktopBridge.addEventListener("message", receiveDesktop);
        desktopBridge.postMessage({type:"bilibiliLogin", requestId, mode});
      } else {
        window.addEventListener("gamenarrator-bilibili-login-response", receive);
        window.dispatchEvent(new CustomEvent("gamenarrator-bilibili-login-request", {
          detail:{requestId, mode}
        }));
      }
    });
  }
  document.querySelectorAll("[data-bilibili-login]").forEach(button => button.addEventListener("click", async () => {
    const buttons = [...document.querySelectorAll("[data-bilibili-login]")];
    buttons.forEach(item => { item.disabled = true; });
    const mode = button.dataset.bilibiliLogin;
    bilibiliLoginState.textContent = mode === "QR"
      ? "正在打开 B站官方页面，请扫码完成登录…"
      : "正在打开 B站官方页面，请在官方页面输入账号密码…";
    try {
      cookieToken = await requestBilibiliLogin(mode);
      rememberAuthentication("https://www.bilibili.com/");
      bilibiliLoginState.textContent = "已连接 Bilibili，本次会话可直接解析登录内容";
      window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-login-success'));
    } catch (error) {
      bilibiliLoginState.textContent = error.message;
      window.gameNarratorDiagnosticEvent?.(error.message, `bilibili-login-${mode.toLowerCase()}`);
    } finally {
      buttons.forEach(item => { item.disabled = false; });
    }
  }));
  function requestBilibiliArticle(sourceUrl) {
    return new Promise((resolve, reject) => {
      const requestId = crypto.randomUUID();
      const desktopBridge = window.chrome?.webview;
      const timeout = setTimeout(() => finish(new Error("等待 Bilibili 专栏页面超时")), 90000);
      const finish = (error, value) => {
        clearTimeout(timeout);
        desktopBridge?.removeEventListener("message", desktopReceive);
        window.removeEventListener("gamenarrator-bilibili-article-response", browserReceive);
        error ? reject(error) : resolve(value);
      };
      const desktopReceive = event => {
        if (event.data?.type !== "bilibiliArticleResponse" || event.data?.requestId !== requestId) return;
        finish(event.data.error ? new Error(event.data.error) : null, event.data.article);
      };
      const browserReceive = event => {
        if (event.detail?.requestId !== requestId) return;
        finish(event.detail.error ? new Error(event.detail.error) : null, event.detail.article);
      };
      if (desktopBridge) {
        desktopBridge.addEventListener("message", desktopReceive);
        desktopBridge.postMessage({type:"bilibiliArticle", requestId, sourceUrl});
      } else {
        window.addEventListener("gamenarrator-bilibili-article-response", browserReceive);
        window.dispatchEvent(new CustomEvent("gamenarrator-bilibili-article-request", {detail:{requestId,sourceUrl}}));
      }
    });
  }
  document.querySelector("[data-bilibili-article-extract]")?.addEventListener("click", async event => {
    const sourceUrl = document.querySelector("[data-bilibili-article-url]")?.value?.trim();
    const box = document.querySelector("[data-bilibili-article-result]");
    if (!/^https:\/\/(?:www\.)?bilibili\.com\/(?:read\/cv\d+|opus\/\d+)/i.test(sourceUrl || "")) {
      box.textContent = "请输入有效的 Bilibili 专栏或动态文章地址"; return;
    }
    event.target.disabled = true; box.textContent = "正在打开 B站页面并提取图文…";
    try {
      const article = await requestBilibiliArticle(sourceUrl);
      const images = [...new Set(article.images || [])].slice(0,30);
      await Promise.allSettled(images.map((url,index) => request("/api/assets/references", {
        provider:"BILIBILI", sourceUrl, previewUrl:url, downloadUrl:null,
        title:`${article.title || "B站专栏"} · 图片 ${index+1}`, creator:article.author || null,
        assetType:"MEME", licenseCode:"RIGHTS_REVIEW_REQUIRED", licenseUrl:null,
        attribution:"Bilibili 专栏提取图片；使用前必须确认转载、修改与商用权限",
        platformTags:["Bilibili专栏","专栏图片","待权利确认"]
      })));
      box.className = "bilibili-article-result";
      box.innerHTML = `<h4>${escapeHtml(article.title || "未命名专栏")}</h4><small>${escapeHtml(article.author || "未知作者")} · 已提取 ${images.length} 张图片</small><p>${escapeHtml(article.text || "未提取到正文")}</p><div class="bilibili-article-images">${images.map(url => `<img src="${escapeHtml(url)}" loading="lazy" referrerpolicy="no-referrer">`).join("")}</div>`;
      document.dispatchEvent(new CustomEvent("asset-library-updated"));
    } catch (error) {
      box.textContent = `${error.message}。如内容需要登录，请先完成上方 Bilibili 登录。`;
      window.gameNarratorDiagnosticEvent?.(error.message,"bilibili-article");
    } finally { event.target.disabled = false; }
  });
  function showResolvedMedia(media) {
    resolvedMedia = media;
    const preview = media.thumbnailPreviewUrl || media.thumbnail;
    result.innerHTML = `<article class="resolved-media">
      <div class="media-cover">${preview ? `<img src="${escapeHtml(preview)}" alt="${escapeHtml(media.title)} 封面" referrerpolicy="no-referrer">` : ""}<span>${preview ? "封面加载失败" : "该视频没有可用封面"}</span></div>
      <div><small>${escapeHtml(media.platform)} · ${Math.round(media.durationSeconds||0)} 秒</small>
      <h3>${escapeHtml(media.title)}</h3><p>${escapeHtml(media.creator||"未知创作者")}</p>
      <div class="content-origin-assessment"><strong>${escapeHtml(media.contentOriginLabel || "来源性质未知")}</strong><span>置信度 ${Math.round((media.originConfidence || 0) * 100)}%</span><p>${escapeHtml(media.originReason || "公开元数据不足")}</p><small>AI 来源判断不代表版权许可；下载前仍须确认拥有所需权利。</small></div>
      <label>下载格式<select id="media-format">${media.variants.map(item =>
        `<option value="${escapeHtml(item.formatId)}">${escapeHtml(item.label)} · ${escapeHtml(item.extension)} · ${size(item.approximateBytes)}</option>`
      ).join("")}</select></label>
      <label class="effect-toggle"><input id="media-subtitles" type="checkbox" checked>同时保存可用字幕</label>
      <label class="effect-toggle"><input id="media-add-library" type="checkbox" checked>同时加入开放素材库</label>
      <button id="media-preview" class="media-preview-button" type="button">生成流畅预览（约 480p）</button>
      <div class="media-preview-progress" hidden><progress max="100" value="0"></progress><span>等待开始</span></div>
      <button id="media-download" type="button">自动下载所选格式</button>
      <div class="media-download-progress" hidden><progress max="100" value="0"></progress><span>等待开始</span></div></div></article>`;
    const coverImage = result.querySelector(".media-cover img");
    if (coverImage) coverImage.addEventListener("error", () =>
      coverImage.closest(".media-cover").classList.add("cover-error"), {once:true});
    message.textContent = `解析成功：找到 ${media.variants.length} 种格式。`;
    localStorage.setItem(mediaPreferencesKey, JSON.stringify({
      rightsConfirmed:Boolean(form.elements.rightsConfirmed?.checked)
    }));
    if (autoDownloadRequested) {
      autoDownloadRequested = false;
      queueMicrotask(() => result.querySelector('#media-download')?.click());
    } else if (autoPreviewRequested) {
      autoPreviewRequested = false;
      queueMicrotask(() => result.querySelector('#media-preview')?.click());
    }
  }
  window.addEventListener('gamenarrator-platform-download-request', event => {
    const sourceUrl = event.detail?.sourceUrl;
    if (!sourceUrl) return;
    form.elements.url.value = sourceUrl;
    form.elements.rightsConfirmed.checked = true;
    autoDownloadRequested = event.detail.autoDownload === true;
    form.requestSubmit();
  });
  window.addEventListener('gamenarrator-platform-preview-request', event => {
    const sourceUrl = event.detail?.sourceUrl;
    if (!sourceUrl) return;
    form.elements.url.value = sourceUrl;
    form.elements.rightsConfirmed.checked = true;
    autoDownloadRequested = false;
    autoPreviewRequested = true;
    form.requestSubmit();
  });
  form.addEventListener("submit", async event => {
    event.preventDefault();
    const data = new FormData(form);
    resolvedUrl = data.get("url");
    resolvedMedia = null;
    const cookieFile = data.get("cookieFile");
    cookieToken = "";
    message.textContent = "正在读取视频信息和可用格式…";
    result.innerHTML = "";
    try {
      if (cookieFile && cookieFile.size > 0) {
        message.textContent = "正在安全导入当前平台的临时 Cookie…";
        cookieToken = await uploadCookieFile(cookieFile, resolvedUrl);
      } else if (remembersAuthentication(resolvedUrl)) {
        message.textContent = "正在恢复此平台的浏览器登录状态…";
        cookieToken = await requestBrowserAuthentication(resolvedUrl).catch(() => "");
      }
      showResolvedMedia(await resolveMedia(cookieToken));
    } catch(error) {
      if (!cookieToken && (!cookieFile || cookieFile.size === 0) && isAuthenticationError(error)) {
        try {
          message.textContent = "平台要求登录，正在通过浏览器扩展自动读取登录状态…";
          cookieToken = await requestBrowserAuthentication(resolvedUrl);
          rememberAuthentication(resolvedUrl);
          showResolvedMedia(await resolveMedia(cookieToken));
          return;
        } catch(extensionError) {
          message.textContent = `${error.message}；${extensionError.message}。请安装并连接 GameNarrator 登录助手。`;
          return;
        }
      }
      message.textContent = error.message;
    }
  });
  const savedMediaPreferences = readJsonPreference(mediaPreferencesKey);
  if (savedMediaPreferences.rightsConfirmed && form.elements.rightsConfirmed)
    form.elements.rightsConfirmed.checked = true;
  result.addEventListener("click", async event => {
    if (event.target.matches(".media-preview-close")) {
      const preview = event.target.closest(".media-online-preview");
      const video = preview?.querySelector("video");
      if (video) { video.pause(); video.removeAttribute("src"); video.load(); }
      preview?.remove();
      message.textContent = "在线预览已关闭。";
      return;
    }
    if (event.target.id === "media-preview") {
      event.target.disabled = true;
      event.target.textContent = "正在准备预览…";
      message.textContent = "正在生成节省空间的约 480p 预览版本。";
      try {
        const job = await request("/api/media-import/preview-jobs", {
          url:resolvedUrl,formatId:"best",
          subtitles:false,addToLibrary:false,rightsConfirmed:true,
          cookieToken:cookieToken || null,title:resolvedMedia?.title || null,
          creator:resolvedMedia?.creator || null,thumbnail:resolvedMedia?.thumbnail || null,
          durationSeconds:resolvedMedia?.durationSeconds || null,tags:libraryTags()
        });
        const progressBox = result.querySelector(".media-preview-progress");
        const progressBar = progressBox.querySelector("progress");
        const progressText = progressBox.querySelector("span");
        progressBox.hidden = false;
        let status;
        do {
          await wait(700);
          status = await read(`/api/media-import/preview-jobs/${job.id}`);
          const progress = status.progress || {};
          const percent = Math.max(0, Math.min(100, parseFloat(progress.percent) || 0));
          progressBar.value = percent;
          progressText.textContent = `${percent.toFixed(1)}% · ${progress.speed || "计算中"} · 剩余 ${progress.eta || "--"}`;
          if (status.status === "FAILED") throw new Error(status.error || "预览生成失败");
        } while (status.status !== "COMPLETED");
        const output = status.result;
        progressBar.value = 100;
        progressText.textContent = `100% · 轻量预览完成 · ${size(output.sizeBytes)}`;
        result.querySelector(".media-online-preview")?.remove();
        result.querySelector(".resolved-media").insertAdjacentHTML("afterend",
          `<div class="media-online-preview"><button type="button" class="media-preview-close" aria-label="关闭在线预览">×</button><video controls playsinline preload="metadata" src="${escapeHtml(output.downloadUrl)}"></video><small>约 480p 流畅预览 · ${size(output.sizeBytes)}</small></div>`);
        message.textContent = "预览已准备完成，可以在线播放并拖动进度。";
        event.target.textContent = "重新生成预览";
      } catch(error) {
        message.textContent = error.message;
        event.target.textContent = "重试在线预览";
      } finally {
        event.target.disabled = false;
      }
      return;
    }
    if (event.target.id === "media-save-local") {
      const url = event.target.dataset.url;
      const fileName = event.target.dataset.filename || "video.mp4";
      try {
        if (window.showSaveFilePicker) {
          const handle = await window.showSaveFilePicker({suggestedName:fileName});
          const response = await fetch(url);
          if (!response.ok || !response.body) throw new Error("无法读取下载文件");
          const writable = await handle.createWritable();
          await response.body.pipeTo(writable);
          message.textContent = `已保存到你选择的位置：${fileName}`;
        } else {
          const link = document.createElement("a");
          link.href = url; link.download = fileName; document.body.appendChild(link); link.click(); link.remove();
          message.textContent = "已交给浏览器下载；保存位置由浏览器下载设置决定。";
        }
        event.target.disabled = true;
      } catch(error) {
        if (error.name !== "AbortError") message.textContent = error.message;
      }
      return;
    }
    if (event.target.id !== "media-download") return;
    const selectedFormat = document.querySelector("#media-format")?.value || "best";
    event.target.disabled = true; event.target.textContent = "下载中，请勿关闭页面…";
    message.textContent = "正在下载和合并音视频，大文件可能需要较长时间。";
    try {
      const job = await request("/api/media-import/download-jobs", {
        url:resolvedUrl,formatId:selectedFormat,
        subtitles:document.querySelector("#media-subtitles").checked,
        addToLibrary:document.querySelector("#media-add-library").checked,
        rightsConfirmed:true,
        cookieToken:cookieToken || null,
        title:resolvedMedia?.title || null,
        creator:resolvedMedia?.creator || null,
        thumbnail:resolvedMedia?.thumbnail || null,
        durationSeconds:resolvedMedia?.durationSeconds || null,
        tags:libraryTags()
      });
      const progressBox = result.querySelector(".media-download-progress");
      const progressBar = progressBox.querySelector("progress");
      const progressText = progressBox.querySelector("span");
      progressBox.hidden = false;
      let status;
      do {
        await wait(700);
        status = await read(`/api/media-import/download-jobs/${job.id}`);
        const progress = status.progress || {};
        const percent = Math.max(0, Math.min(100, parseFloat(progress.percent) || 0));
        progressBar.value = percent;
        progressText.textContent = `${percent.toFixed(1)}% · ${progress.speed || "计算中"} · 剩余 ${progress.eta || "--"}`;
        if (status.status === "FAILED") throw new Error(status.error || "下载失败");
      } while (status.status !== "COMPLETED");
      const output = status.result;
      progressBar.value = 100;
      progressText.textContent = `100% · 下载和合并完成 · ${size(output.sizeBytes)}`;
      message.textContent = `${output.assetId ? "下载完成并已加入开放素材库" : "下载完成"}（${size(output.sizeBytes)}）。可继续保存到自己的电脑。`;
      if (output.assetId) document.dispatchEvent(new CustomEvent("asset-library-updated", {detail:{assetId:output.assetId}}));
      event.target.textContent = "服务器下载完成";
      event.target.insertAdjacentHTML("afterend", `<button id="media-save-local" type="button"
        data-url="${escapeHtml(output.downloadUrl)}" data-filename="${escapeHtml(output.fileName)}">选择位置保存到电脑</button>`);
    } catch(error) {
      message.textContent = error.message; event.target.disabled = false; event.target.textContent = "重新下载";
    }
  });
})();
