if (!globalThis.__gameNarratorAuthBridgeInstalled) {
  globalThis.__gameNarratorAuthBridgeInstalled = true;
  document.documentElement.dataset.gamenarratorExtensionReady = 'true';
  window.addEventListener('gamenarrator-bilibili-recommend-request', async event => {
    const requestId = event.detail?.requestId;
    if (!requestId) return;
    try {
      const response = await chrome.runtime.sendMessage({type:'GAME_NARRATOR_BILIBILI_RECOMMENDATIONS'});
      if (!response?.ok) throw new Error(response?.error || '扩展没有返回推荐内容');
      const registrations = await Promise.allSettled((response.items || []).map(item => fetch('/api/assets/references', {
        method:'POST', credentials:'same-origin', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
          provider:'BILIBILI', sourceUrl:item.sourceUrl, previewUrl:item.previewUrl || null, downloadUrl:null,
          title:String(item.title || item.bvid).slice(0, 200), creator:item.creator ? String(item.creator).slice(0, 120) : null,
          assetType:'VIDEO', licenseCode:'RIGHTS_REVIEW_REQUIRED', licenseUrl:null,
          attribution:`当前账号 Bilibili 首页推荐${item.metricsText ? `；页面指标：${item.metricsText}` : ''}；下载前必须确认权利`.slice(0, 500),
          platformTags:['Bilibili账号推荐','待权利确认']
        })
      }).then(async result => {
        const body = await result.json().catch(() => ({}));
        if (!result.ok) throw new Error(body.message || `HTTP ${result.status}`);
        return body;
      })));
      const count = registrations.filter(item => item.status === 'fulfilled').length;
      window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-recommend-response', {detail:{requestId, count}}));
    } catch (error) {
      window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-recommend-response', {detail:{requestId, error:error.message}}));
    }
  });
  window.addEventListener('gamenarrator-bilibili-search-request', async event => {
    const requestId = event.detail?.requestId;
    const query = event.detail?.query;
    const assetType = event.detail?.assetType || 'VIDEO';
    if (!requestId || !query) return;
    try {
      const response = await chrome.runtime.sendMessage({type:'GAME_NARRATOR_BILIBILI_SEARCH', query, page:event.detail?.page || 1});
      if (!response?.ok) throw new Error(response?.error || '扩展没有返回 Bilibili 搜索结果');
      const registrations = await Promise.allSettled((response.items || []).map(item => fetch('/api/assets/references', {
        method:'POST', credentials:'same-origin', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
          provider:'BILIBILI', sourceUrl:item.sourceUrl, previewUrl:item.previewUrl || null, downloadUrl:null,
          title:String(item.title || item.bvid).slice(0, 200), creator:item.creator ? String(item.creator).slice(0, 120) : null,
          assetType, licenseCode:'RIGHTS_REVIEW_REQUIRED', licenseUrl:null,
          attribution:`当前账号 Bilibili 搜索结果${item.metricsText ? `；页面指标：${item.metricsText}` : ''}；下载前必须确认权利`.slice(0, 500),
          platformTags:['Bilibili账号搜索',`搜索词:${String(query).slice(0, 80)}`,'待权利确认']
        })
      }).then(async result => {
        const body = await result.json().catch(() => ({}));
        if (!result.ok) throw new Error(body.message || `HTTP ${result.status}`);
        return body;
      })));
      const assets = registrations.filter(item => item.status === 'fulfilled').map(item => item.value);
      window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-search-response', {detail:{requestId, assets}}));
    } catch (error) {
      window.dispatchEvent(new CustomEvent('gamenarrator-bilibili-search-response', {detail:{requestId, error:error.message}}));
    }
  });
  window.dispatchEvent(new CustomEvent("gamenarrator-browser-auth-ready"));
}
