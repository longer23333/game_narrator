(() => {
  const form = document.querySelector("#asset-search-form");
  const list = document.querySelector("#asset-list");
  const message = document.querySelector("#asset-message");
  const refresh = document.querySelector("#asset-refresh");
  const filters = document.querySelector("#asset-filter-form");
  const domesticSources = document.querySelector("#domestic-source-list");
  const loadMore = document.querySelector('#asset-load-more');
  const previousPage = document.querySelector('#asset-previous-page');
  const pageIndicator = document.querySelector('#asset-page-indicator');
  const selectAll = document.querySelector('#asset-select-all');
  const favoriteSelected = document.querySelector('#asset-favorite-selected');
  const localDropzone = document.querySelector('#local-asset-dropzone');
  const localInput = document.querySelector('#local-asset-input');
  const localBrowse = document.querySelector('#local-asset-browse');
  const searchPreferencesKey = 'gameNarrator.publicAssetSearch';
  const recommendationSyncKey = 'gameNarrator.bilibiliRecommendationSyncAt';
  let translationNotice = '';
  let discoveryState = {query:'', assetType:'', provider:'', sort:'RELEVANCE', page:1, pageSize:9, commercialUse:true, allowModification:true, loading:false};

  async function uploadLocalFiles(files) {
    const accepted = [...files].filter(file => /^(video|image|audio)\//.test(file.type));
    if (!accepted.length) { message.textContent = '请选择视频、图片或音频文件。'; return; }
    localDropzone.classList.add('uploading');
    for (let index = 0; index < accepted.length; index++) {
      const file = accepted[index];
      message.textContent = `正在上传并分析 ${index + 1}/${accepted.length}：${file.name}`;
      const body = new FormData(); body.append('file', file);
      await request('/api/assets/upload', {method:'POST', body});
    }
    localDropzone.classList.remove('uploading');
    message.textContent = `已上传并分析 ${accepted.length} 个本地素材。`;
    await load(false);
  }

  localBrowse?.addEventListener('click', () => localInput.click());
  localInput?.addEventListener('change', () => uploadLocalFiles(localInput.files).catch(error => {
    localDropzone.classList.remove('uploading'); message.textContent = error.message;
  }));
  for (const type of ['dragenter','dragover']) localDropzone?.addEventListener(type, event => {
    event.preventDefault(); localDropzone.classList.add('dragging');
  });
  for (const type of ['dragleave','drop']) localDropzone?.addEventListener(type, event => {
    event.preventDefault(); localDropzone.classList.remove('dragging');
  });
  localDropzone?.addEventListener('drop', event => uploadLocalFiles(event.dataTransfer.files).catch(error => {
    localDropzone.classList.remove('uploading'); message.textContent = error.message;
  }));
  if (!form || !list) return;

  const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);

  async function request(url, options = {}) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `请求失败（HTTP ${response.status}）`);
    return body;
  }

  async function refreshTranslationNotice() {
    try {
      const settings = await request('/api/ai-settings');
      translationNotice = settings.mode === 'CLOUD' && !settings.apiKeyConfigured
        ? '；尚未绑定云端 AI，英文素材先保留原名。请在顶部“AI 模型设置”填写 API Key 后再生成中文标题'
        : '';
    } catch { translationNotice = ''; }
  }

  async function loadDomesticSources() {
    if (!domesticSources) return;
    try {
      const sources = await request('/api/assets/sources');
      const query = String(form.elements.query?.value || '').trim();
      const target = source => source.searchUrl && query
        ? source.searchUrl.replace('{query}', encodeURIComponent(query)) : source.url;
      const sourceLink = source => `<a href="${escapeHtml(target(source))}" target="_blank"
        rel="noopener noreferrer" title="授权需在原站确认"><b>${source.region === 'INTERNATIONAL' ? '海外' : '国内'}</b>
        ${escapeHtml(source.name)}<small>${escapeHtml(source.assetTypes || '综合素材')}</small></a>`;
      const domestic = sources.filter(source => source.region !== 'INTERNATIONAL');
      const international = sources.filter(source => source.region === 'INTERNATIONAL');
      domesticSources.innerHTML = `<section><strong>国内来源（优先）</strong><div>${domestic.map(sourceLink).join('')}</div></section>
        <section><strong>海外视频与音效</strong><div>${international.map(sourceLink).join('')}</div></section>`;
    } catch (error) {
      domesticSources.textContent = '国内素材站目录暂时无法加载。';
    }
  }

  async function externalJson(url) {
    const response = await fetch(url, {signal: AbortSignal.timeout(5000)});
    if (!response.ok) throw new Error(`开放接口返回 HTTP ${response.status}`);
    return response.json();
  }

  const cleanExternalText = value => {
    const node = document.createElement('div');
    node.innerHTML = String(value || '').replace(/<[^>]+>/g, ' ');
    return (node.textContent || '').replace(/\s+/g, ' ').trim();
  };

  async function browserDiscover(query, assetType, pageSize = 6, page = 1, provider = '') {
    const candidates = [];
    if ((!provider || provider === 'OPENVERSE') && assetType !== 'VIDEO') {
      const endpoint = ['MEME', 'IMAGE'].includes(assetType) ? 'images' : 'audio';
      const params = new URLSearchParams({q: query, page_size: String(pageSize), page: String(page), mature: 'false', license: 'cc0,pdm,by,by-sa'});
      try {
        const data = await externalJson(`https://api.openverse.org/v1/${endpoint}/?${params}`);
        for (const item of data.results || []) candidates.push({
          provider:'OPENVERSE', sourceUrl: item.foreign_landing_url, previewUrl: item.thumbnail || item.url, downloadUrl: item.url,
          title: item.title || '未命名素材', creator: item.creator || null, assetType,
          licenseCode: item.license || 'unknown', licenseUrl: item.license_url || null,
          attribution: item.attribution || null,
          platformTags: (item.tags || []).map(tag => typeof tag === 'string' ? tag : tag.name).filter(Boolean).slice(0, 20)
        });
      } catch (error) {
        console.warn('[GameNarrator] 浏览器 Openverse 回退失败', error);
      }
    }
    if (!provider || provider === 'WIKIMEDIA') {
      const params = new URLSearchParams({action:'query', generator:'search', gsrsearch:query,
        gsrnamespace:'6', gsrlimit:String(pageSize), gsroffset:String((page - 1) * pageSize), prop:'imageinfo', iiprop:'url|mime|extmetadata',
        iiurlwidth:'640', format:'json', formatversion:'2', origin:'*'});
      try {
        const data = await externalJson(`https://commons.wikimedia.org/w/api.php?${params}`);
        const prefix = assetType === 'VIDEO' ? 'video/' : ['MEME', 'IMAGE'].includes(assetType) ? 'image/' : 'audio/';
        for (const page of data.query?.pages || []) {
          const info = page.imageinfo?.[0];
          if (!info?.mime?.startsWith(prefix)) continue;
          const meta = info.extmetadata || {};
          const license = cleanExternalText(meta.LicenseShortName?.value || 'unknown');
          if (/\b(?:NC|ND)\b/i.test(license)) continue;
          candidates.push({provider:'WIKIMEDIA', sourceUrl: info.descriptionurl, previewUrl: info.thumburl || info.url, downloadUrl: info.url,
            title: String(page.title || '未命名素材').replace(/^File:/, ''),
            creator: cleanExternalText(meta.Artist?.value).slice(0, 120) || null, assetType,
            licenseCode: license.slice(0, 40), licenseUrl: meta.LicenseUrl?.value || null,
            attribution: cleanExternalText(meta.Credit?.value).slice(0, 500) || null, platformTags: []});
        }
      } catch (error) {
        console.warn('[GameNarrator] 浏览器 Wikimedia 回退失败', error);
      }
    }
    const valid = candidates.filter(item => item.sourceUrl?.startsWith('https://'));
    if (!valid.length) throw new Error('浏览器也无法连接开放素材接口');
    const registrations = await Promise.allSettled(valid.map(item => request('/api/assets/references', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
        ...item, title:String(item.title || '未命名素材').slice(0, 200),
        creator:item.creator ? String(item.creator).slice(0, 120) : null,
        previewUrl:item.previewUrl?.startsWith('https://') ? item.previewUrl : null,
        downloadUrl:item.downloadUrl?.startsWith('https://') ? item.downloadUrl : null,
        attribution:item.attribution ? String(item.attribution).slice(0, 500) : null
      })
    })));
    const imported = registrations.filter(result => result.status === 'fulfilled').map(result => result.value);
    if (!imported.length) throw new Error('开放素材已找到，但无法登记到本地素材库');
    return imported;
  }

  function inferAssetType(query, selected) {
    const text = String(query || '').toLowerCase();
    if (/(表情包|梗图|斗图|meme|reaction|sticker)/i.test(text)) return 'MEME';
    if (/(图片|照片|贴图|image|photo)/i.test(text)) return 'IMAGE';
    if (/(音效|声音|sfx|sound effect)/i.test(text)) return 'SFX';
    if (/(背景音乐|配乐|音乐|bgm|music)/i.test(text)) return 'BGM';
    if (/(绿幕|视频|green screen|video)/i.test(text)) return 'VIDEO';
    return selected;
  }

  function tagMarkup(tag) {
    const source = tag.userAdded ? "用户" : (tag.sources || []).join("+") || "系统";
    return `<span class="asset-tag ${tag.userAdded ? "user" : ""}" title="${escapeHtml(source)}">
      ${escapeHtml(tag.name)}<small>${escapeHtml(source)}</small>
      <button type="button" data-remove-tag="${escapeHtml(tag.name)}" aria-label="移除标签">×</button>
    </span>`;
  }

  function formatDuration(durationMs) {
    const totalSeconds = Math.round(Number(durationMs || 0) / 1000);
    if (!totalSeconds) return '';
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return hours
      ? `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
      : `${minutes}:${String(seconds).padStart(2, '0')}`;
  }

  function mediaMarkup(asset) {
    const localPreview = asset.importStatus === "DOWNLOADED" ? `/api/assets/${asset.id}/preview` : "";
    const remoteThumbnail = !asset.previewUrl ? '' : asset.provider === 'BILIBILI'
      ? `/api/assets/${asset.id}/thumbnail`
      : asset.previewUrl;
    const mediaUrl = localPreview || remoteThumbnail;
    if (["MEME", "IMAGE"].includes(asset.assetType)) {
      if (!mediaUrl) return "";
      return `<img class="asset-preview-image" src="${escapeHtml(mediaUrl)}" alt="${escapeHtml(asset.title)}" loading="lazy" referrerpolicy="no-referrer">`;
    }
    if (asset.assetType === "VIDEO") {
      const poster = remoteThumbnail;
      return `<button type="button" class="asset-video-thumbnail" data-online-preview
        data-preview-url="${escapeHtml(localPreview || asset.downloadUrl || '')}"
        data-source-url="${escapeHtml(asset.landingUrl || '')}"
        data-provider="${escapeHtml(asset.provider || '')}"
        data-poster-url="${escapeHtml(poster)}"
        aria-label="在线预览 ${escapeHtml(asset.title)}">
        ${poster ? `<img src="${escapeHtml(poster)}" alt="${escapeHtml(asset.title)} 缩略图" loading="lazy" referrerpolicy="no-referrer">` : '<span>暂无缩略图</span>'}
        <b>▶ 在线预览</b>
      </button>`;
    }
    if (asset.provider === 'BILIBILI' && asset.importStatus === 'REFERENCE_ONLY') {
      return `<div class="asset-video-thumbnail asset-platform-audio-candidate">
        ${remoteThumbnail ? `<img src="${escapeHtml(remoteThumbnail)}" alt="${escapeHtml(asset.title)} 封面" loading="lazy" referrerpolicy="no-referrer">` : '<span>暂无封面</span>'}
        <b>视频音轨候选</b>
      </div>`;
    }
    const audioUrl = localPreview || `/api/assets/${asset.id}/remote-preview`;
    return `<button type="button" class="asset-audio-thumbnail" data-online-preview data-media-kind="audio"
      data-preview-url="${escapeHtml(audioUrl)}" aria-label="在线试听 ${escapeHtml(asset.localizedTitle || asset.title)}">
      <span>♫</span><b>在线试听</b><small>点击后才加载音频</small>
    </button>`;
  }

  function primaryAssetAction(asset) {
    if (asset.importStatus === 'REFERENCE_ONLY' && ['BILIBILI','YOUTUBE','DOUYIN','TIKTOK'].includes(asset.provider)) {
      return `<button type="button" data-platform-import data-source-url="${escapeHtml(asset.landingUrl)}">确认权利并下载</button>`;
    }
    const download = `<button type="button" data-download>${asset.importStatus === "DOWNLOADED" ? "已下载" : "下载到素材库"}</button>`;
    if (asset.assetType !== 'VIDEO') return download;
    return `${download}<button type="button" data-derive="FRAME">下载一个画面</button><button type="button" data-derive="AUDIO">仅下载声音</button>`;
  }

  function render(assets, append = false) {
    if (!assets.length) {
      if (!append) list.innerHTML = '<p class="empty compact">没有匹配素材，试试英文关键词。</p>';
      return;
    }
    const existing = new Set([...list.querySelectorAll('.asset-card')].map(card => card.dataset.id));
    const fresh = append ? assets.filter(asset => !existing.has(String(asset.id))) : assets;
    const markup = fresh.map(asset => `
      <article class="asset-card" data-id="${asset.id}">
        ${mediaMarkup(asset)}
        <div class="asset-card-body">
          <div class="asset-card-head">
            <div><small>${escapeHtml(asset.provider)} · ${escapeHtml(asset.assetType)}</small>
              <h3>${escapeHtml(asset.localizedTitle || asset.title || "未命名素材")}</h3>
              ${asset.localizedTitle && asset.localizedTitle !== asset.title ? `<small class="asset-original-title">原名：${escapeHtml(asset.title)}</small>` : ''}
            </div>
            <div class="asset-state-actions"><label title="选择此素材"><input type="checkbox" data-select-asset aria-label="选择 ${escapeHtml(asset.localizedTitle || asset.title || '素材')}"></label><button type="button" data-favorite title="收藏">${asset.favorite ? "★" : "☆"}</button><button type="button" data-archive>${asset.archived ? "恢复" : "归档"}</button><span>${escapeHtml(asset.licenseCode || "unknown")}</span></div>
          </div>
          <p>${escapeHtml(asset.creator || "未知作者")}${asset.durationMs ? ` · ${formatDuration(asset.durationMs)}` : ''}</p>
          <div class="asset-tags">${(asset.tags || []).map(tagMarkup).join("")}</div>
          <form class="asset-tag-form">
            <input name="tag" maxlength="100" placeholder="添加用户标签">
            <button type="submit">添加</button>
          </form>
          <div class="asset-actions">
            <a href="${escapeHtml(asset.landingUrl)}" target="_blank" rel="noopener noreferrer">查看来源与许可</a>
            <div>${primaryAssetAction(asset)}
            <button type="button" data-similar>AI 找相似</button>
            <button type="button" class="asset-delete" data-delete-asset>删除</button></div>
          </div>
          <small class="asset-attribution">${escapeHtml(asset.attribution || "使用前请在原页面确认署名要求")}</small>
        </div>
      </article>`).join("");
    if (append) list.insertAdjacentHTML('beforeend', markup);
    else list.innerHTML = markup;
  }

  let libraryLoadController;
  let libraryLoadSequence = 0;
  async function load(semantic = false) {
    const sequence = ++libraryLoadSequence;
    libraryLoadController?.abort();
    libraryLoadController = new AbortController();
    try {
      const values = new FormData(filters);
      const params = new URLSearchParams();
      for (const name of ["query", "assetType", "provider", "importStatus", "sort"]) {
        if (values.get(name)) params.set(name, values.get(name));
      }
      if (values.get("favorite") === "on") params.set("favorite", "true");
      params.set("archived", values.get("archived") === "on" ? "true" : "false");
      params.set("semantic", String(semantic));
      params.set("limit", "9");
      const response = await fetch(`/api/assets?${params}`, {signal:libraryLoadController.signal});
      if (!response.ok) throw new Error((await response.json().catch(() => ({}))).message || `请求失败（${response.status}）`);
      const assets = await response.json();
      if (sequence === libraryLoadSequence) render(assets);
    } catch (error) {
      if (error.name === 'AbortError') return;
      message.textContent = error.message;
    }
  }

  async function loadFeatured() {
    message.textContent = "正在加载开放素材推荐…";
    try {
      const assets = await request("/api/assets/discover/featured");
      render(assets);
      message.textContent = `已展示 ${assets.length} 项开放素材，可使用下方搜索和筛选快速查找。`;
    } catch (error) {
      message.textContent = '正在切换开放素材读取方式…';
      try {
        const attempts = await Promise.allSettled([
          browserDiscover('funny reaction', 'MEME', 3), browserDiscover('green screen footage', 'VIDEO', 3),
          browserDiscover('game sound effect', 'SFX', 3), browserDiscover('background music', 'BGM', 3)
        ]);
        const assets = attempts.filter(result => result.status === 'fulfilled').flatMap(result => result.value);
        if (!assets.length) throw new Error('浏览器也无法连接开放素材接口');
        render(assets);
        message.textContent = `已展示 ${assets.length} 项开放素材。${translationNotice}`;
      } catch (browserError) {
        message.textContent = `在线来源均不可用，下面仅展示本地已有素材：${browserError.message}`;
        await load();
      }
    }
  }

  function updatePagination(page, resultCount) {
    if (pageIndicator) pageIndicator.textContent = `第 ${page} 页 · 每页 9 项`;
    if (previousPage) {
      previousPage.hidden = false;
      previousPage.disabled = page <= 1 || discoveryState.loading;
    }
    if (loadMore) {
      loadMore.hidden = false;
      loadMore.disabled = resultCount < discoveryState.pageSize || discoveryState.loading;
      loadMore.textContent = resultCount < discoveryState.pageSize ? '没有下一页' : '下一页';
    }
  }

  async function discoverPublicAssets(page) {
    if (discoveryState.loading) return;
    if (discoveryState.provider === 'BILIBILI' && ['MEME', 'IMAGE'].includes(discoveryState.assetType)) {
      message.textContent = 'Bilibili 返回的是视频候选，不能归类为 Meme 或普通图片；请选择“视频 / 绿幕”，或改用开放图片来源。';
      return;
    }
    discoveryState.loading = true;
    let renderedCount = 0;
    loadMore.disabled = true;
    message.textContent = page === 1 ? '正在检索多个开放素材源并生成 AI 标签…' : `正在加载第 ${page} 页公共素材…`;
    let bilibiliAssets = [];
    let bilibiliExtensionError = '';
    if (discoveryState.provider === 'BILIBILI') {
      try {
        message.textContent = `正在通过当前账号检索 Bilibili 第 ${page} 页…`;
        bilibiliAssets = await searchBilibiliWithExtension(discoveryState.query, discoveryState.assetType, page);
      } catch (extensionError) {
        bilibiliExtensionError = extensionError.message;
        console.info('[GameNarrator] 当前账号 Bilibili 搜索暂不可用：', extensionError.message);
      }
    }
    try {
      const remoteAssets = await request("/api/assets/discover", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          query: discoveryState.query,
          assetType: discoveryState.assetType,
          pageSize: discoveryState.pageSize,
          page,
          commercialUse: discoveryState.commercialUse,
          allowModification: discoveryState.allowModification,
          provider: discoveryState.provider || null,
          sort: discoveryState.sort
        })
      });
      const assets = [...new Map([...remoteAssets, ...bilibiliAssets].map(asset => [String(asset.id), asset])).values()]
        .slice(0, discoveryState.pageSize);
      render(assets, false);
      renderedCount = assets.length;
      discoveryState.page = page;
      updatePagination(page, assets.length);
      message.textContent = (assets.length ? `已加载第 ${page} 页，共返回 ${assets.length} 项公共素材。` : '没有更多公共素材了。') + translationNotice;
    } catch (error) {
      if (bilibiliAssets.length) {
        const assets = bilibiliAssets.slice(0, discoveryState.pageSize);
        render(assets, false);
        renderedCount = assets.length;
        discoveryState.page = page;
        updatePagination(page, assets.length);
        message.textContent = `已通过当前 Bilibili 账号加载第 ${page} 页，共 ${assets.length} 项搜索结果；其他公共源暂不可用。`;
        return;
      }
      try {
        const assets = await browserDiscover(discoveryState.query, discoveryState.assetType, discoveryState.pageSize, page, discoveryState.provider);
        const pageAssets = assets.slice(0, discoveryState.pageSize);
        render(pageAssets, false);
        renderedCount = pageAssets.length;
        discoveryState.page = page;
        updatePagination(page, pageAssets.length);
        message.textContent = `已通过浏览器直接加载第 ${page} 页，共 ${pageAssets.length} 项开放素材。${translationNotice}`;
      } catch (browserError) {
        if (page === 1) {
          if (filters?.elements.query) filters.elements.query.value = discoveryState.query;
          if (filters?.elements.assetType) filters.elements.assetType.value = discoveryState.assetType;
          await load(false);
        }
        const extensionDetail = bilibiliExtensionError ? `；B站扩展搜索失败：${bilibiliExtensionError}` : '';
        message.textContent = `${error.message}${extensionDetail}；开放接口浏览器回退也失败${page > 1 ? '。' : '，已显示库内匹配的缓存素材。'}`;
      }
    } finally {
      discoveryState.loading = false;
      updatePagination(discoveryState.page, renderedCount);
    }
  }

  function searchBilibiliWithExtension(query, assetType, page) {
    return new Promise((resolve, reject) => {
      const desktopBridge = window.chrome?.webview;
      if (!desktopBridge && document.documentElement.dataset.gamenarratorExtensionReady !== 'true') {
        reject(new Error('请先登录 Bilibili；网页端还需连接浏览器助手'));
        return;
      }
      const requestId = crypto.randomUUID();
      const timeout = setTimeout(() => {
        window.removeEventListener('gamenarrator-bilibili-search-response', receive);
        desktopBridge?.removeEventListener('message', receiveDesktop);
        reject(new Error('读取 Bilibili 搜索结果超时，请确认已登录'));
      }, 35000);
      function receive(event) {
        if (event.detail?.requestId !== requestId) return;
        clearTimeout(timeout);
        window.removeEventListener('gamenarrator-bilibili-search-response', receive);
        if (event.detail.error) reject(new Error(event.detail.error));
        else resolve(event.detail.assets || []);
      }
      async function receiveDesktop(event) {
        if (event.data?.type !== 'bilibiliAssetsResponse' || event.data?.requestId !== requestId) return;
        clearTimeout(timeout); desktopBridge.removeEventListener('message', receiveDesktop);
        if (event.data.error) { reject(new Error(event.data.error)); return; }
        try { resolve(await registerBilibiliItems(event.data.items || [], assetType, query)); }
        catch (error) { reject(error); }
      }
      if (desktopBridge) {
        desktopBridge.addEventListener('message', receiveDesktop);
        desktopBridge.postMessage({type:'bilibiliAssets', requestId, mode:'SEARCH', query, page});
      } else {
        window.addEventListener('gamenarrator-bilibili-search-response', receive);
        window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-search-request', {detail:{requestId, query, assetType, page}}));
      }
    });
  }

  async function registerBilibiliItems(items, assetType = 'VIDEO', query = '') {
    const settled = await Promise.allSettled(items.map(item => request('/api/assets/references', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
        provider:'BILIBILI', sourceUrl:item.sourceUrl, previewUrl:item.previewUrl || null, downloadUrl:null,
        title:String(item.title || item.bvid).slice(0,200), creator:item.creator ? String(item.creator).slice(0,120) : null,
        assetType, licenseCode:'RIGHTS_REVIEW_REQUIRED', licenseUrl:null,
        attribution:`当前账号 Bilibili ${query ? '搜索结果' : '首页推荐'}${item.metricsText ? `；页面指标：${item.metricsText}` : ''}；下载前必须确认权利`.slice(0,500),
        platformTags:[query ? 'Bilibili账号搜索' : 'Bilibili账号推荐', ...(query ? [`搜索词:${String(query).slice(0,80)}`] : []), '待权利确认']
      })
    })));
    return settled.filter(value => value.status === 'fulfilled').map(value => value.value);
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const values = new FormData(form);
    const assetType = inferAssetType(values.get('query'), values.get('assetType'));
    form.elements.assetType.value = assetType;
    if (filters?.elements.query) filters.elements.query.value = values.get('query');
    if (filters?.elements.assetType) filters.elements.assetType.value = assetType;
    discoveryState = {query:String(values.get('query') || '').trim(), assetType,
      provider:String(values.get('provider') || ''), sort:String(values.get('sort') || 'RELEVANCE'),
      page:1, pageSize:9,
      commercialUse:values.get('commercialUse') === 'on', allowModification:values.get('allowModification') === 'on', loading:false};
    localStorage.setItem(searchPreferencesKey, JSON.stringify({...discoveryState, loading:undefined, page:undefined}));
    loadMore.hidden = true;
    await refreshTranslationNotice();
    await discoverPublicAssets(1);
  });

  loadMore?.addEventListener('click', () => discoverPublicAssets(discoveryState.page + 1));
  previousPage?.addEventListener('click', () => discoverPublicAssets(Math.max(1, discoveryState.page - 1)));

  selectAll?.addEventListener('click', () => {
    const boxes = [...list.querySelectorAll('[data-select-asset]')];
    const shouldSelect = boxes.some(box => !box.checked);
    boxes.forEach(box => { box.checked = shouldSelect; });
    selectAll.textContent = shouldSelect ? '取消全选' : '全选当前结果';
  });

  favoriteSelected?.addEventListener('click', async () => {
    const cards = [...list.querySelectorAll('.asset-card')]
      .filter(card => card.querySelector('[data-select-asset]')?.checked);
    if (!cards.length) {
      message.textContent = '请先选择要收藏的公共素材。';
      return;
    }
    favoriteSelected.disabled = true;
    const results = await Promise.allSettled(cards.map(card => request(`/api/assets/${card.dataset.id}/state`, {
      method:'PATCH', headers:{'Content-Type':'application/json'}, body:JSON.stringify({favorite:true})
    })));
    const succeeded = results.filter(result => result.status === 'fulfilled').length;
    cards.forEach((card, index) => {
      if (results[index].status === 'fulfilled') {
        const star = card.querySelector('[data-favorite]');
        if (star) star.textContent = '★';
        card.querySelector('[data-select-asset]').checked = false;
      }
    });
    favoriteSelected.disabled = false;
    selectAll.textContent = '全选当前结果';
    message.textContent = `已收藏 ${succeeded} 项素材${succeeded < cards.length ? `，${cards.length - succeeded} 项失败` : ''}。`;
  });

  try {
    const saved = JSON.parse(localStorage.getItem(searchPreferencesKey) || '{}');
    for (const name of ['query', 'assetType', 'provider', 'sort']) {
      if (saved[name] !== undefined && form.elements[name]) form.elements[name].value = String(saved[name]);
    }
    if (form.elements.pageSize) form.elements.pageSize.value = '9';
    for (const name of ['commercialUse', 'allowModification']) {
      if (typeof saved[name] === 'boolean' && form.elements[name]) form.elements[name].checked = saved[name];
    }
  } catch (error) {
    localStorage.removeItem(searchPreferencesKey);
  }

  list.addEventListener("submit", async event => {
    if (!event.target.matches(".asset-tag-form")) return;
    event.preventDefault();
    const card = event.target.closest(".asset-card");
    const input = event.target.elements.tag;
    if (!input.value.trim()) return;
    await updateTags(card.dataset.id, [input.value.trim()], []);
  });

  list.addEventListener("click", async event => {
    const card = event.target.closest(".asset-card");
    if (!card) return;
    try {
      if (event.target.matches("[data-remove-tag]")) {
        await updateTags(card.dataset.id, [], [event.target.dataset.removeTag]);
      } else if (event.target.closest("[data-online-preview]")) {
        const trigger = event.target.closest('[data-online-preview]');
        const previewUrl = trigger.dataset.previewUrl;
        if (!previewUrl && trigger.dataset.sourceUrl) {
          window.dispatchEvent(new CustomEvent('gamenarrator-platform-preview-request', {
            detail:{sourceUrl:trigger.dataset.sourceUrl}
          }));
          document.querySelector('#media-resolve-form')?.scrollIntoView({behavior:'smooth', block:'center'});
          message.textContent = `正在解析 ${trigger.dataset.provider || '平台'} 视频并生成在线预览…`;
          return;
        }
        if (!previewUrl) throw new Error('该素材没有可用的在线预览地址');
        const audio = trigger.dataset.mediaKind === 'audio';
        closeOnlinePreview();
        const overlay = document.createElement('div');
        overlay.className = 'asset-online-preview';
        overlay.innerHTML = `<div><button type="button" data-close-online-preview aria-label="关闭预览">×</button>
          ${audio
            ? `<audio controls autoplay preload="metadata" src="${escapeHtml(previewUrl)}"></audio>`
            : `<video controls autoplay playsinline preload="metadata" poster="${escapeHtml(trigger.dataset.posterUrl || '')}" src="${escapeHtml(previewUrl)}"></video>`}
          <small>${audio ? '在线流式试听' : '临时流式预览'} · 不保存到素材库 · 播放结束自动关闭</small></div>`;
        document.body.appendChild(overlay);
        overlay.querySelector('video,audio').addEventListener('ended', closeOnlinePreview, {once:true});
      } else if (event.target.matches("[data-platform-import]")) {
        if (!window.confirm('请确认这是本人作品、已获授权，或平台明确允许下载和再创作的内容。确认后将自动调用平台下载器。')) return;
        const importForm = document.querySelector('#media-resolve-form');
        const urlInput = importForm?.querySelector('[name="url"]');
        if (!urlInput) throw new Error('未找到平台素材导入区');
        window.dispatchEvent(new CustomEvent('gamenarrator-platform-download-request', {
          detail:{sourceUrl:event.target.dataset.sourceUrl, autoDownload:true}
        }));
        importForm.scrollIntoView({behavior:'smooth', block:'center'});
        message.textContent = '已确认权利，正在通过现有 Bilibili 下载器解析并下载。';
      } else if (event.target.matches("[data-download]")) {
        event.target.disabled = true;
        event.target.textContent = "下载中…";
        await request(`/api/assets/${card.dataset.id}/download`, {method: "POST"});
        message.textContent = "素材已下载到本地素材库。";
        await load();
      } else if (event.target.matches("[data-derive]")) {
        const mode = event.target.dataset.derive;
        let timestampSeconds = 0;
        if (mode === 'FRAME') {
          const answer = window.prompt('输入要截取的时间（秒）', '0');
          if (answer === null) return;
          timestampSeconds = Number(answer);
          if (!Number.isFinite(timestampSeconds) || timestampSeconds < 0) throw new Error('时间必须是大于等于 0 的秒数');
        }
        event.target.disabled = true;
        event.target.textContent = mode === 'FRAME' ? '正在下载并截帧…' : '正在下载并提取声音…';
        await request(`/api/assets/${card.dataset.id}/derive`, {method:'POST', headers:{'Content-Type':'application/json'},
          body:JSON.stringify({mode, timestampSeconds})});
        message.textContent = mode === 'FRAME' ? '画面已提取并同步到公共素材库。' : '音轨已提取并同步到公共素材库。';
        await load();
      } else if (event.target.matches("[data-delete-asset]")) {
        if (!window.confirm("确定删除这项素材及其 data 文件吗？原文件、抠图文件和派生缓存都会永久删除。")) return;
        event.target.disabled = true;
        event.target.textContent = "删除中…";
        await request(`/api/assets/${card.dataset.id}`, {method: "DELETE"});
        card.remove();
        message.textContent = "素材记录及 data 中的关联文件已删除。";
      } else if (event.target.matches("[data-similar]")) {
        event.target.disabled = true;
        message.textContent = "BGE 正在查找语义相似素材…";
        const assets = await request(`/api/assets/${card.dataset.id}/similar`);
        render(assets);
        message.textContent = assets.length ? `已找到 ${assets.length} 项语义相似素材。` : "没有找到足够相似的素材。";
      } else if (event.target.matches("[data-favorite]")) {
        await request(`/api/assets/${card.dataset.id}/state`, {method:"PATCH",
          headers:{"Content-Type":"application/json"}, body:JSON.stringify({favorite:event.target.textContent !== "★"})});
        await load();
      } else if (event.target.matches("[data-archive]")) {
        await request(`/api/assets/${card.dataset.id}/state`, {method:"PATCH",
          headers:{"Content-Type":"application/json"}, body:JSON.stringify({archived:event.target.textContent !== "恢复"})});
        await load();
      }
    } catch (error) {
      message.textContent = error.message;
      await load();
    }
  });

  function closeOnlinePreview() {
    const overlay = document.querySelector('.asset-online-preview');
    const media = overlay?.querySelector('video,audio');
    if (media) {
      media.pause();
      media.removeAttribute('src');
      media.load();
    }
    overlay?.remove();
  }

  document.addEventListener('click', event => {
    if (event.target.matches('[data-close-online-preview]') || event.target.matches('.asset-online-preview')) {
      closeOnlinePreview();
    }
  });

  async function updateTags(id, add, remove) {
    await request(`/api/assets/${id}/tags`, {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({add, remove})
    });
    message.textContent = "用户标签已保存，并覆盖默认分类结果。";
    await load();
  }

  refresh?.addEventListener("click", loadFeatured);
  let filterTimer;
  filters?.addEventListener("input", () => {
    clearTimeout(filterTimer);
    filterTimer = setTimeout(() => load(false), 250);
  });
  filters?.addEventListener("change", () => {
    clearTimeout(filterTimer);
    load(false);
  });
  filters?.addEventListener("submit", event => {
    event.preventDefault();
    clearTimeout(filterTimer);
    message.textContent = '正在进行 AI 语义排序…';
    load(true);
  });
  document.addEventListener("asset-library-updated", () => load(false));
  let initialized = false;
  function initializeAssetLibrary() {
    if (initialized) return;
    initialized = true;
    Promise.allSettled([loadDomesticSources(), refreshTranslationNotice(), load(false)]);
    window.requestIdleCallback
      ? window.requestIdleCallback(syncBilibiliRecommendations, {timeout: 3000})
      : setTimeout(syncBilibiliRecommendations, 1200);
  }
  const assetSection = form.closest('section') || form;
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      if (!entries.some(entry => entry.isIntersecting)) return;
      observer.disconnect();
      initializeAssetLibrary();
    }, {rootMargin:'400px 0px'});
    observer.observe(assetSection);
  } else {
    initializeAssetLibrary();
  }

  function syncBilibiliRecommendations(force = false) {
    const previous = Number(localStorage.getItem(recommendationSyncKey) || 0);
    if (!force && Date.now() - previous < 10 * 60 * 1000) return;
    const requestId = crypto.randomUUID();
    const desktopBridge = window.chrome?.webview;
    const receive = async event => {
      if (event.detail?.requestId !== requestId) return;
      window.removeEventListener('gamenarrator-bilibili-recommend-response', receive);
      if (event.detail.error) {
        console.info('[GameNarrator] Bilibili 推荐内容暂未同步：', event.detail.error);
        return;
      }
      localStorage.setItem(recommendationSyncKey, String(Date.now()));
      if (event.detail.count > 0) {
        message.textContent = `已从当前 Bilibili 账号同步 ${event.detail.count} 项首页推荐记录（尚未下载）。`;
        await load();
      }
    };
    const receiveDesktop = async event => {
      if (event.data?.type !== 'bilibiliAssetsResponse' || event.data?.requestId !== requestId) return;
      desktopBridge.removeEventListener('message', receiveDesktop);
      if (event.data.error) { console.info('[GameNarrator] Bilibili 推荐内容暂未同步：', event.data.error); return; }
      const assets = await registerBilibiliItems(event.data.items || []);
      localStorage.setItem(recommendationSyncKey, String(Date.now()));
      if (assets.length) { message.textContent = `已从当前 Bilibili 账号同步 ${assets.length} 项首页推荐记录（尚未下载）。`; await load(); }
    };
    if (desktopBridge) {
      desktopBridge.addEventListener('message', receiveDesktop);
      desktopBridge.postMessage({type:'bilibiliAssets', requestId, mode:'RECOMMEND'});
    } else {
      window.addEventListener('gamenarrator-bilibili-recommend-response', receive);
      window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-recommend-request', {detail:{requestId}}));
    }
    setTimeout(() => window.removeEventListener('gamenarrator-bilibili-recommend-response', receive), 30000);
  }
  window.addEventListener('gamenarrator-browser-auth-ready', () => syncBilibiliRecommendations(false));
  window.addEventListener('gamenarrator-bilibili-login-success', () => syncBilibiliRecommendations(true));
})();
