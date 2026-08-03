const ALLOWED_SOURCE_HOSTS = ['bilibili.com', 'b23.tv', 'youtube.com', 'youtu.be', 'douyin.com', 'tiktok.com'];
const ALLOWED_COOKIE_DOMAINS = new Set(['bilibili.com', 'youtube.com', 'google.com', 'douyin.com', 'tiktok.com']);
const matchesHost = (host, suffix) => host === suffix || host.endsWith(`.${suffix}`);

chrome.runtime.onInstalled.addListener(() => restoreConnectedContentScripts());
chrome.runtime.onStartup.addListener(() => restoreConnectedContentScripts());
restoreConnectedContentScripts();

async function restoreConnectedContentScripts() {
  const stored = await chrome.storage.local.get('connectedOrigins');
  for (const value of stored.connectedOrigins || []) {
    try {
      const origin = new URL(value).origin;
      const id = scriptId(origin);
      await chrome.scripting.unregisterContentScripts({ids:[id]}).catch(() => {});
      await chrome.scripting.registerContentScripts([{
        id, matches:[`${origin}/*`], js:['content-script.js'], persistAcrossSessions:true, runAt:'document_start'
      }]);
    } catch (error) {
      console.warn('Unable to restore a connected GameNarrator origin', error.message);
    }
  }
}

function scriptId(origin) {
  return `gamenarrator-${btoa(origin).replace(/[^a-z0-9]/gi, '').slice(0, 32)}`;
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request?.type === 'GAME_NARRATOR_READ_COOKIES') {
    readPlatformCookies(request.sourceUrl, request.cookieDomains, sender).then(sendResponse)
      .catch(error => sendResponse({ok:false, error:error.message || '无法读取平台登录状态'}));
    return true;
  }
  if (request?.type === 'GAME_NARRATOR_BILIBILI_LOGIN') {
    loginBilibili(request.mode, sender).then(sendResponse)
      .catch(error => sendResponse({ok:false, error:error.message || 'Bilibili 登录失败'}));
    return true;
  }
  if (request?.type === 'GAME_NARRATOR_BILIBILI_ARTICLE') {
    readBilibiliArticle(request.sourceUrl, sender).then(article => sendResponse({ok:true, article}))
      .catch(error => sendResponse({ok:false, error:error.message || 'Bilibili 专栏提取失败'}));
    return true;
  }
  if (request?.type === 'GAME_NARRATOR_BILIBILI_RECOMMENDATIONS') {
    readBilibiliRecommendations(sender).then(items => sendResponse({ok:true, items}))
      .catch(error => sendResponse({ok:false, error:error.message || '无法读取 Bilibili 推荐内容'}));
    return true;
  }
  if (request?.type === 'GAME_NARRATOR_BILIBILI_SEARCH') {
    readBilibiliSearch(request.query, request.page, sender).then(items => sendResponse({ok:true, items}))
      .catch(error => sendResponse({ok:false, error:error.message || '无法读取 Bilibili 搜索结果'}));
    return true;
  }
  return false;
});

async function requireConnectedOrigin(sender) {
  const senderOrigin = sender.tab?.url ? new URL(sender.tab.url).origin : '';
  const stored = await chrome.storage.local.get('connectedOrigins');
  if (!Array.isArray(stored.connectedOrigins) || !stored.connectedOrigins.includes(senderOrigin)) {
    throw new Error('当前 GameNarrator 尚未连接扩展，请点击扩展图标完成一次连接');
  }
  return senderOrigin;
}

async function readPlatformCookies(sourceUrl, requestedDomains, sender) {
  const source = new URL(sourceUrl);
  if (source.protocol !== 'https:') throw new Error('只支持 HTTPS 平台地址');
  await requireConnectedOrigin(sender);
  if (!ALLOWED_SOURCE_HOSTS.some(host => matchesHost(source.hostname, host))) throw new Error('扩展尚未配置该视频平台');
  const cookieDomains = Array.isArray(requestedDomains)
    ? [...new Set(requestedDomains.map(String).map(value => value.toLowerCase()))] : [];
  if (!cookieDomains.length || cookieDomains.some(domain => !ALLOWED_COOKIE_DOMAINS.has(domain))) {
    throw new Error('网站请求了扩展未授权的 Cookie 域名');
  }
  const all = [];
  for (const domain of cookieDomains) all.push(...await chrome.cookies.getAll({domain}));
  const unique = new Map(all.map(cookie => [`${cookie.domain}\t${cookie.path}\t${cookie.name}`, cookie]));
  if (!unique.size) throw new Error('浏览器中没有找到该平台登录状态，请先登录平台网站');
  const lines = ['# Netscape HTTP Cookie File'];
  for (const cookie of unique.values()) lines.push([
    cookie.domain, cookie.domain.startsWith('.') ? 'TRUE' : 'FALSE', cookie.path || '/',
    cookie.secure ? 'TRUE' : 'FALSE', cookie.session ? '0' : Math.floor(cookie.expirationDate || 0),
    cookie.name, cookie.value
  ].join('\t'));
  return {ok:true, cookieText:`${lines.join('\n')}\n`};
}

function cookieText(cookies) {
  const unique = new Map(cookies.map(cookie => [`${cookie.domain}\t${cookie.path}\t${cookie.name}`, cookie]));
  const lines = ['# Netscape HTTP Cookie File'];
  for (const cookie of unique.values()) lines.push([
    cookie.domain, cookie.domain.startsWith('.') ? 'TRUE' : 'FALSE', cookie.path || '/',
    cookie.secure ? 'TRUE' : 'FALSE', cookie.session ? '0' : Math.floor(cookie.expirationDate || 0),
    cookie.name, cookie.value
  ].join('\t'));
  return `${lines.join('\n')}\n`;
}

async function loginBilibili(mode, sender) {
  await requireConnectedOrigin(sender);
  const current = await chrome.cookies.getAll({domain:'bilibili.com'});
  if (hasBilibiliSession(current)) return {ok:true, cookieText:cookieText(current)};
  const tab = await chrome.tabs.create({url:'https://www.bilibili.com/', active:true});
  let completed = false;
  try {
    await waitForTab(tab.id, 30000);
    await chrome.scripting.executeScript({target:{tabId:tab.id}, func:openBilibiliLogin, args:[mode]}).catch(() => {});
    const deadline = Date.now() + 180000;
    while (Date.now() < deadline) {
      const cookies = await chrome.cookies.getAll({domain:'bilibili.com'});
      if (hasBilibiliSession(cookies)) {
        completed = true;
        return {ok:true, cookieText:cookieText(cookies)};
      }
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    throw new Error('等待 Bilibili 登录超时；登录页已保留，可完成登录后重试');
  } finally {
    if (completed && tab?.id) await chrome.tabs.remove(tab.id).catch(() => {});
  }
}

function hasBilibiliSession(cookies) {
  const names = new Set(cookies.map(cookie => cookie.name));
  return names.has('SESSDATA') && names.has('DedeUserID');
}

function openBilibiliLogin(mode) {
  const clickMatching = (selectors, pattern) => {
    for (const element of document.querySelectorAll(selectors)) {
      if (pattern.test((element.textContent || '').trim())) { element.click(); return true; }
    }
    return false;
  };
  clickMatching('button,a,div,span', /^登录$/);
  if (mode === 'ACCOUNT') setTimeout(() => clickMatching('button,a,div,span', /密码登录|账号登录/), 700);
}

async function readBilibiliArticle(sourceUrl, sender) {
  await requireConnectedOrigin(sender);
  const url = new URL(sourceUrl);
  if (url.protocol !== 'https:' || !matchesHost(url.hostname, 'bilibili.com')
      || !/^\/(?:read\/cv\d+|opus\/\d+)/.test(url.pathname)) throw new Error('Bilibili 专栏地址无效');
  const tab = await chrome.tabs.create({url:url.href, active:false});
  try {
    await waitForTab(tab.id, 30000);
    for (let attempt=0; attempt<12; attempt++) {
      const result = await chrome.scripting.executeScript({target:{tabId:tab.id}, func:scrapeBilibiliArticle});
      const article = result?.[0]?.result;
      if (article?.text?.length > 20 || article?.images?.length) return article;
      await new Promise(resolve => setTimeout(resolve,1000));
    }
    throw new Error('页面已打开，但没有检测到专栏正文；如内容受限请先登录 Bilibili');
  } finally { if (tab?.id) await chrome.tabs.remove(tab.id).catch(() => {}); }
}

function scrapeBilibiliArticle() {
  const clean = value => String(value || '').replace(/\s+/g,' ').trim();
  const root = document.querySelector('#article-content, .article-content, .opus-module-content, [class*="article-content"], article') || document.body;
  const title = clean(document.querySelector('h1, .title, [class*="title"]')?.textContent || document.title.replace(/_哔哩哔哩.*$/,''));
  const author = clean(document.querySelector('.up-name, .author-name, [class*="author"]')?.textContent);
  const text = [...root.querySelectorAll('p,h2,h3,blockquote,li')].map(item => clean(item.textContent)).filter(Boolean).join('\n');
  const images = [...root.querySelectorAll('img')].map(image => image.currentSrc || image.src || image.dataset.src || '')
    .map(url => url.startsWith('//') ? `https:${url}` : url.replace(/^http:/,'https:'))
    .filter(url => /^https:\/\//.test(url) && !/face|avatar|logo/i.test(url));
  return {title,author,text:text.slice(0,50000),images:[...new Set(images)].slice(0,50)};
}

async function readBilibiliRecommendations(sender) {
  return readBilibiliPage('https://www.bilibili.com/', sender);
}

async function readBilibiliSearch(query, page, sender) {
  const keyword = String(query || '').trim();
  if (!keyword || keyword.length > 100) throw new Error('Bilibili 搜索词无效');
  const pageNumber = Math.max(1, Math.min(50, Number(page) || 1));
  return readBilibiliPage(`https://search.bilibili.com/all?keyword=${encodeURIComponent(keyword)}&page=${pageNumber}`, sender);
}

async function readBilibiliPage(url, sender) {
  await requireConnectedOrigin(sender);
  const tab = await chrome.tabs.create({url, active:false});
  try {
    await waitForTab(tab.id, 20000);
    for (let attempt = 0; attempt < 8; attempt++) {
      const result = await chrome.scripting.executeScript({target:{tabId:tab.id}, func:scrapeRecommendations});
      const items = (result?.[0]?.result || []).slice(0, 30);
      if (items.length) return items;
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    throw new Error('Bilibili 页面已打开，但没有检测到视频卡片；请确认当前账号可以正常访问该页面');
  } finally {
    if (tab?.id) await chrome.tabs.remove(tab.id).catch(() => {});
  }
}

function waitForTab(tabId, timeoutMs) {
  return new Promise((resolve, reject) => {
    let done = false;
    const finish = error => {
      if (done) return; done = true; clearTimeout(timer); chrome.tabs.onUpdated.removeListener(listener);
      error ? reject(error) : resolve();
    };
    const listener = (id, info) => { if (id === tabId && info.status === 'complete') finish(); };
    const timer = setTimeout(() => finish(new Error('Bilibili 推荐页加载超时')), timeoutMs);
    chrome.tabs.onUpdated.addListener(listener);
    chrome.tabs.get(tabId).then(current => { if (current.status === 'complete') finish(); }).catch(finish);
  });
}

function scrapeRecommendations() {
  const clean = value => String(value || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  const validTitle = value => value && value.length >= 2 && value.length <= 200
    && !/^(?:添加至)?稍后再看/i.test(value)
    && !/^[\d.]+(?:万|亿)?\s*[\d.]+(?:万|亿)?\s*\d{1,2}:\d{2}$/.test(value);
  const found = new Map();
  for (const anchor of document.querySelectorAll('a[href*="/video/BV"]')) {
    const match = anchor.href.match(/https?:\/\/(?:www\.)?bilibili\.com\/video\/(BV[0-9A-Za-z]+)/i);
    if (!match || found.has(match[1])) continue;
    const card = anchor.closest('.bili-video-card, .feed-card, [class*="video-card"], [class*="feed-card"]') || anchor.parentElement;
    const titleNode = card?.querySelector('.bili-video-card__info--tit a, .bili-video-card__info--tit, .video-name, h3 a, h3, a[title][href*="/video/BV"]');
    const title = [anchor.getAttribute('title'), anchor.getAttribute('aria-label'),
      titleNode?.getAttribute?.('title'), titleNode?.textContent]
      .map(clean).find(validTitle);
    if (!title) continue;
    const image = card?.querySelector('img');
    const rawImage = image?.currentSrc || image?.src || image?.getAttribute('data-src') || '';
    const normalizedImage = rawImage.startsWith('//') ? `https:${rawImage}` : rawImage.replace(/^http:/, 'https:');
    const author = card?.querySelector('[class*="author"], [class*="up-name"], [class*="owner"]');
    const metrics = [];
    for (const node of card?.querySelectorAll('[class*="play"], [class*="danmaku"], [class*="duration"]') || []) {
      const value = clean(node.textContent); if (value && value.length <= 40 && !metrics.includes(value)) metrics.push(value);
      if (metrics.length >= 4) break;
    }
    found.set(match[1], {bvid:match[1], sourceUrl:`https://www.bilibili.com/video/${match[1]}`,
      title, creator:clean(author?.textContent), previewUrl:normalizedImage.startsWith('https://') ? normalizedImage : '',
      metricsText:metrics.join(' / ')});
    if (found.size >= 30) break;
  }
  return [...found.values()];
}
