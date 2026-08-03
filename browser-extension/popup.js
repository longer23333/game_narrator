const statusNode = document.querySelector('#status');
const connectPanel = document.querySelector('#connect-panel');
const syncPanel = document.querySelector('#sync-panel');
const resultsNode = document.querySelector('#results');
let activeTab;
let detected = [];

const setStatus = (text, kind = '') => {
  statusNode.className = kind;
  statusNode.textContent = text;
};
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const isBilibiliSearch = url => {
  try { const parsed = new URL(url); return (parsed.hostname === 'bilibili.com' || parsed.hostname.endsWith('.bilibili.com')) && parsed.pathname.startsWith('/'); }
  catch { return false; }
};

document.querySelector('#connect').addEventListener('click', async () => {
  try {
    if (!activeTab?.url) throw new Error('无法读取当前页面');
    const url = new URL(activeTab.url);
    if (!/^https?:$/.test(url.protocol)) throw new Error('请先打开 GameNarrator 网页');
    const originPattern = `${url.origin}/*`;
    if (!await chrome.permissions.request({origins:[originPattern]})) throw new Error('没有获得当前网站权限');
    const stored = await chrome.storage.local.get('connectedOrigins');
    const origins = new Set(stored.connectedOrigins || []); origins.add(url.origin);
    await chrome.storage.local.set({connectedOrigins:[...origins], activeGameNarratorOrigin:url.origin});
    const id = `gamenarrator-${btoa(url.origin).replace(/[^a-z0-9]/gi, '').slice(0, 32)}`;
    await chrome.scripting.unregisterContentScripts({ids:[id]}).catch(() => {});
    await chrome.scripting.registerContentScripts([{
      id, matches:[originPattern], js:['content-script.js'], persistAcrossSessions:true, runAt:'document_start'
    }]);
    await chrome.scripting.executeScript({target:{tabId:activeTab.id}, files:['content-script.js']});
    setStatus('连接成功。现在打开 Bilibili 搜索页，再点击扩展图标即可批量同步。', 'ok');
  } catch (error) { setStatus(error.message, 'error'); }
});

document.querySelector('#select-all').addEventListener('change', event => {
  resultsNode.querySelectorAll('input[type=checkbox]').forEach(box => { box.checked = event.target.checked; });
});
document.querySelector('#rights').addEventListener('change', event => { document.querySelector('#sync').disabled = !event.target.checked; });

document.querySelector('#sync').addEventListener('click', async event => {
  try {
    const selected = [...resultsNode.querySelectorAll('input[data-index]:checked')]
      .map(box => detected[Number(box.dataset.index)]).filter(Boolean);
    if (!selected.length) throw new Error('请至少选择一项搜索结果');
    const stored = await chrome.storage.local.get('activeGameNarratorOrigin');
    const origin = stored.activeGameNarratorOrigin;
    if (!origin) throw new Error('尚未连接 GameNarrator，请先在应用页面点击扩展完成连接');
    event.target.disabled = true;
    const assetType = document.querySelector('#asset-type').value;
    const requests = selected.map(item => fetch(`${origin}/api/assets/references`, {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
        provider:'BILIBILI', sourceUrl:item.sourceUrl, previewUrl:item.previewUrl || null, downloadUrl:null,
        title:item.title.slice(0, 200), creator:item.creator?.slice(0, 120) || null, assetType,
        licenseCode:'RIGHTS_REVIEW_REQUIRED', licenseUrl:null,
        attribution:`来自 Bilibili 已登录浏览器搜索页${item.metricsText ? `；页面指标：${item.metricsText}` : ''}；下载和再创作前必须确认权利`.slice(0, 500),
        platformTags:['Bilibili搜索同步', ...(item.tags || [])].slice(0, 20)
      })
    }).then(async response => {
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
      return body;
    }));
    const settled = await Promise.allSettled(requests);
    const success = settled.filter(result => result.status === 'fulfilled').length;
    if (!success) throw new Error(settled.find(result => result.status === 'rejected')?.reason?.message || '同步失败');
    setStatus(`已同步 ${success} 项到公共素材库${success < selected.length ? `，${selected.length - success} 项失败` : ''}。`, success === selected.length ? 'ok' : 'error');
  } catch (error) { setStatus(error.message, 'error'); }
  finally { event.target.disabled = !document.querySelector('#rights').checked; }
});

function scrapeBilibiliResults() {
  const clean = value => String(value || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  const validTitle = value => value && value.length >= 2 && value.length <= 200
    && !/^(?:添加至)?稍后再看/i.test(value)
    && !/^[\d.]+(?:万|亿)?\s*[\d.]+(?:万|亿)?\s*\d{1,2}:\d{2}$/.test(value);
  const found = new Map();
  for (const anchor of document.querySelectorAll('a[href*="/video/BV"]')) {
    const match = anchor.href.match(/https?:\/\/(?:www\.)?bilibili\.com\/video\/(BV[0-9A-Za-z]+)/i);
    if (!match || found.has(match[1])) continue;
    const card = anchor.closest('.bili-video-card, .video-item, [class*="video-card"]') || anchor.parentElement;
    const titleNode = card?.querySelector('.bili-video-card__info--tit a, .bili-video-card__info--tit, .video-name, h3 a, h3, a[title][href*="/video/BV"]');
    const image = card?.querySelector('img');
    const author = card?.querySelector('[class*="author"], [class*="up-name"], [class*="owner"]');
    const rawImage = image?.currentSrc || image?.src || image?.getAttribute('data-src') || '';
    const normalizedImage = rawImage.startsWith('//') ? `https:${rawImage}` : rawImage.replace(/^http:/, 'https:');
    const previewUrl = normalizedImage.startsWith('https://') ? normalizedImage : '';
    const title = [anchor.getAttribute('title'), anchor.getAttribute('aria-label'),
      titleNode?.getAttribute?.('title'), titleNode?.textContent]
      .map(clean).find(validTitle);
    if (!title) continue;
    const cardText = clean(card?.textContent);
    const metricParts = [];
    for (const node of card?.querySelectorAll('[class*="play"], [class*="danmaku"], [class*="stat"], [class*="duration"]') || []) {
      const value = clean(node.textContent);
      if (value && value.length <= 40 && !metricParts.includes(value)) metricParts.push(value);
      if (metricParts.length >= 4) break;
    }
    const metricsText = metricParts.join(' / ');
    const tags = ['Bilibili搜索结果'];
    if (/\b\d{1,2}:\d{2}\b/.test(cardText)) tags.push('含时长信息');
    found.set(match[1], {bvid:match[1], sourceUrl:`https://www.bilibili.com/video/${match[1]}`,
      title, creator:clean(author?.textContent), previewUrl, metricsText, tags});
    if (found.size >= 50) break;
  }
  return [...found.values()];
}

(async () => {
  [activeTab] = await chrome.tabs.query({active:true, currentWindow:true});
  if (!activeTab?.url || !isBilibiliSearch(activeTab.url)) return;
  connectPanel.classList.add('hidden'); syncPanel.classList.remove('hidden');
  try {
    const injection = await chrome.scripting.executeScript({target:{tabId:activeTab.id}, func:scrapeBilibiliResults});
    detected = injection?.[0]?.result || [];
    document.querySelector('#summary').textContent = `检测到 ${detected.length} 个当前页面视频结果。`;
    resultsNode.innerHTML = detected.map((item, index) => `<label class="result"><input type="checkbox" data-index="${index}" checked>
      ${item.previewUrl ? `<img src="${escapeHtml(item.previewUrl)}">` : '<span></span>'}<span><b>${escapeHtml(item.title)}</b><small>${escapeHtml(item.creator || item.bvid)}</small></span></label>`).join('');
    if (!detected.length) setStatus('当前页面没有检测到视频卡片，请等待搜索结果加载后重新打开扩展。', 'error');
  } catch (error) { setStatus(`读取搜索结果失败：${error.message}`, 'error'); }
})();
