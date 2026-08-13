const taskList = document.querySelector('#task-list');
const activeTaskPanel = document.querySelector('#active-task');
const validViews = new Set(['studio', 'search', 'import', 'assets', 'settings']);
const lazyScriptPromises = new Map();
const loadedLazyFeatures = new Set();
const lazyScriptUrls = {
  diagnostics:'/diagnostics.js?v=20260811-1',
  readiness:'/release-readiness.js?v=20260813-1',
  updates:'/updates.js?v=20260811-1',
  export:'/export.js?v=20260728-4'
};

const runtimeRestartButton = document.querySelector('#runtime-restart');
const desktopBridge = window.chrome?.webview;
if (runtimeRestartButton && desktopBridge) {
  runtimeRestartButton.hidden = false;
  runtimeRestartButton.addEventListener('click', () => {
    runtimeRestartButton.disabled = true;
    runtimeRestartButton.textContent = '正在重新运行…';
    const localAi = document.querySelector('#ai-settings-form [name="mode"]')?.value === 'LOCAL';
    desktopBridge.postMessage({
      type:'restartApplication',
      requestId:crypto.randomUUID?.() || String(Date.now()),
      localAi
    });
  });
}

function lazyScriptFailureMessage(key, error) {
  const detail = String(error?.message || error || '未知错误');
  if (/failed to fetch|fetch dynamically imported module|networkerror/i.test(detail)) {
    return `无法加载${key}功能脚本：前端开发服务器可能已经停止。请在项目目录运行 npm run dev:frontend，确认 http://127.0.0.1:5173 可访问后刷新页面。`;
  }
  return `无法加载${key}功能脚本：${detail}`;
}

function loadLazyScript(key) {
  if (lazyScriptPromises.has(key)) return lazyScriptPromises.get(key);
  const promise = import(/* @vite-ignore */ lazyScriptUrls[key]).then(module => {
    loadedLazyFeatures.add(key);
    return module;
  }).catch(error => {
    lazyScriptPromises.delete(key);
    throw new Error(lazyScriptFailureMessage(key, error));
  });
  lazyScriptPromises.set(key, promise);
  return promise;
}

async function ensureViewScripts(view) {
  if (view === 'import' || view === 'assets') await window.gameNarratorModules?.load(view);
}

function activateView(view, updateHistory = false) {
  const selected = validViews.has(view) ? view : 'studio';
  document.body.dataset.view = selected;
  document.querySelectorAll('[data-page]').forEach(section => { section.hidden = section.dataset.page !== selected; });
  document.querySelectorAll('[data-view-link]').forEach(link => {
    const active = link.dataset.viewLink === selected;
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  });
  const labels = {studio:'剪辑任务', search:'镜头搜索', import:'平台导入', assets:'素材库', settings:'AI 与系统设置'};
  document.title = `${labels[selected]} · GameNarrator`;
  ensureViewScripts(selected).catch(error => {
    const page = document.querySelector(`[data-page="${selected}"]`);
    const message = page?.querySelector('.asset-library-note,.empty');
    if (message) message.textContent = `${error.message}，请刷新页面重试。`;
  });
  if (updateHistory) history.pushState({view:selected}, '', `/?view=${selected}`);
  window.scrollTo({top:0, behavior:'instant'});
}

activateView(new URLSearchParams(location.search).get('view'));
document.querySelector('.primary-nav')?.addEventListener('click', event => {
  const link = event.target.closest('[data-view-link]');
  if (!link) return;
  event.preventDefault();
  activateView(link.dataset.viewLink, true);
});
window.addEventListener('popstate', () => activateView(new URLSearchParams(location.search).get('view')));
const utilityMenu = document.querySelector('.utility-menu');
utilityMenu?.addEventListener('click', event => {
  if (event.target.closest('button')) utilityMenu.open = false;
});
document.addEventListener('click', event => {
  if (utilityMenu?.open && !utilityMenu.contains(event.target)) utilityMenu.open = false;
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && utilityMenu?.open) utilityMenu.open = false;
});
document.querySelector('#diagnostics-open')?.addEventListener('click', async event => {
  if (loadedLazyFeatures.has('diagnostics')) return;
  event.preventDefault();
  const button = event.currentTarget;
  button.disabled = true;
  try {
    await loadLazyScript('diagnostics');
    const dialog = document.querySelector('#diagnostics-dialog');
    if (dialog && !dialog.open) dialog.showModal();
    document.querySelector('#diagnostics-refresh')?.click();
  } catch (error) {
    window.alert(error.message);
  } finally {
    button.disabled = false;
  }
});
document.querySelector('#readiness-open')?.addEventListener('click', async () => {
  if (loadedLazyFeatures.has('readiness')) return;
  await loadLazyScript('readiness');
  loadedLazyFeatures.add('readiness');
  document.querySelector('#readiness-open')?.click();
}, {once:true});
document.querySelector('#updates-open')?.addEventListener('click', async event => {
  if (loadedLazyFeatures.has('updates')) return;
  event.preventDefault(); const button = event.currentTarget; button.disabled = true;
  try { await loadLazyScript('updates'); document.querySelector('#updates-dialog')?.showModal(); }
  catch (error) { window.alert(error.message); } finally { button.disabled = false; }
});
if (localStorage.getItem('gameNarrator.lastSeenRelease') === '2.2.28') document.querySelector('#updates-open')?.classList.remove('has-update');
window.addEventListener('gamenarrator-release-jump', event => {
  const {view='studio', selector, note} = event.detail || {}; activateView(view, true);
  setTimeout(() => {
    const target = selector ? document.querySelector(selector) : null;
    if (target) { target.scrollIntoView({behavior:'smooth', block:'center'}); target.classList.add('release-highlight'); setTimeout(() => target.classList.remove('release-highlight'), 2600); }
    else if (note) window.alert(note);
  }, 350);
});
const aiSettingsForm = document.querySelector('#ai-settings-form');
const aiKeyState = document.querySelector('#ai-key-state');
const modelServiceState = document.querySelector('[data-model-service]');
const installedModels = document.querySelector('[data-installed-models]');
const recommendedModels = document.querySelector('[data-recommended-models]');
const aiProviderPresets = {
  DASHSCOPE:['https://dashscope.aliyuncs.com/compatible-mode/v1','qwen-vl-plus','qwen-plus'], DEEPSEEK:['https://api.deepseek.com','qwen2.5vl:3b','deepseek-chat'],
  OPENAI:['https://api.openai.com/v1','gpt-4.1','gpt-4.1-mini'], ANTHROPIC:['https://api.anthropic.com/v1','claude-sonnet-4-20250514','claude-sonnet-4-20250514'],
  GEMINI:['https://generativelanguage.googleapis.com/v1beta','gemini-2.5-pro','gemini-2.5-flash'], OPENROUTER:['https://openrouter.ai/api/v1','',''],
  SILICONFLOW:['https://api.siliconflow.cn/v1','Qwen/Qwen2.5-VL-72B-Instruct','deepseek-ai/DeepSeek-V3'], MOONSHOT:['https://api.moonshot.cn/v1','','moonshot-v1-128k'],
  ZHIPU:['https://open.bigmodel.cn/api/paas/v4','glm-4v-plus','glm-4-plus'], VOLCENGINE:['https://ark.cn-beijing.volces.com/api/v3','',''],
  BAIDU:['https://qianfan.baidubce.com/v2','',''], TENCENT:['https://api.hunyuan.cloud.tencent.com/v1','',''], MINIMAX:['https://api.minimax.chat/v1','',''],
  XAI:['https://api.x.ai/v1','grok-2-vision-1212','grok-3-mini'], MISTRAL:['https://api.mistral.ai/v1','pixtral-large-latest','mistral-large-latest'],
  GROQ:['https://api.groq.com/openai/v1','',''], TOGETHER:['https://api.together.xyz/v1','',''], PERPLEXITY:['https://api.perplexity.ai','','sonar'], CEREBRAS:['https://api.cerebras.ai/v1','','']
};

async function loadAiSettings() {
  if (!aiSettingsForm) return;
  const response = await fetch('/api/ai-settings');
  if (!response.ok) return;
  const value = await response.json();
  for (const name of ['mode','provider','baseUrl','visionModel','textModel','inputPricePerMillion','outputPricePerMillion','cachedInputPricePerMillion'])
    if (aiSettingsForm.elements[name] && value[name]) aiSettingsForm.elements[name].value = value[name];
  aiKeyState.textContent = value.apiKeyConfigured ? `API Key：${value.apiKeyMasked}` : '尚未配置云端 API Key';
  aiSettingsForm.querySelector('[data-cloud-settings]').hidden = value.mode === 'LOCAL';
  updateAiProviderHint();
}

const modelResources = model => `内存约 ${Number(model.memoryMb).toLocaleString()} MB · 显存约 ${Number(model.vramMb).toLocaleString()} MB`;
function modelCard(model, installed) {
  const active = [model.activeVision ? '视觉正在使用' : '', model.activeText ? '文案正在使用' : ''].filter(Boolean).join(' · ');
  return `<article class="local-model-card ${active ? 'active' : ''}"><header><div><strong>${escapeHtml(model.name)}</strong><small>${escapeHtml(model.kind || '通用')}</small></div><i>${installed ? escapeHtml(model.health) : '可下载'}</i></header><p>${escapeHtml(model.description || modelResources(model))}</p>${model.description ? `<small>${escapeHtml(modelResources(model))}</small>` : ''}<footer>${installed ? `<button type="button" data-model-action="switch" data-model-role="VISION" data-model-name="${escapeHtml(model.name)}" ${model.activeVision ? 'disabled' : ''}>设为视觉</button><button type="button" data-model-action="switch" data-model-role="TEXT" data-model-name="${escapeHtml(model.name)}" ${model.activeText ? 'disabled' : ''}>设为文案</button>` : `<button type="button" data-model-action="download" data-model-name="${escapeHtml(model.name)}">下载模型</button>`}<span>${escapeHtml(active)}</span></footer></article>`;
}
async function loadLocalModels() {
  if (!modelServiceState) return;
  modelServiceState.textContent = '正在检测 Ollama 和已安装模型…';
  const response = await fetch('/api/ai-settings/models', {cache:'no-store'});
  if (!response.ok) throw await readApiError(response);
  const catalog = await response.json();
  modelServiceState.textContent = catalog.message;
  modelServiceState.className = catalog.serviceAvailable ? 'model-service-ready' : 'model-service-offline';
  installedModels.innerHTML = catalog.installed.length ? catalog.installed.map(model => modelCard(model, true)).join('') : '<p class="empty">尚未发现已安装模型。</p>';
  const installedNames = new Set(catalog.installed.map(model => model.name));
  recommendedModels.innerHTML = catalog.recommendations.filter(model => !installedNames.has(model.name)).map(model => modelCard(model, false)).join('') || '<p class="empty">推荐模型均已安装。</p>';
}
document.querySelector('.local-model-manager')?.addEventListener('click', async event => {
  const button = event.target.closest('[data-model-action]'); if (!button) return;
  button.disabled = true;
  try {
    if (button.dataset.modelAction === 'refresh') return await loadLocalModels();
    const endpoint = button.dataset.modelAction === 'download' ? 'download' : 'switch';
    modelServiceState.textContent = endpoint === 'download' ? `正在下载 ${button.dataset.modelName}，请勿关闭应用…` : '正在切换模型…';
    const response = await fetch(`/api/ai-settings/models/${endpoint}`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({name:button.dataset.modelName, role:button.dataset.modelRole})});
    if (!response.ok) throw await readApiError(response);
    await Promise.all([loadAiSettings(), loadLocalModels()]);
  } catch (error) { modelServiceState.textContent = error.message; }
  finally { button.disabled = false; }
});

function updateAiProviderHint() {
  if (!aiSettingsForm) return;
  const deepSeek = aiSettingsForm.elements.provider.value === 'DEEPSEEK';
  document.querySelector('#ai-provider-hint').textContent = deepSeek
    ? 'DeepSeek API 用于文案生成；视频画面分析自动使用本地视觉模型，首次使用会确认下载安装。'
    : '云端视觉和文案均使用当前服务。';
}

aiSettingsForm?.elements.mode.addEventListener('change', event => {
  aiSettingsForm.querySelector('[data-cloud-settings]').hidden = event.target.value === 'LOCAL';
});
aiSettingsForm?.elements.provider.addEventListener('change', event => {
  const preset=aiProviderPresets[event.target.value];
  if(preset) [aiSettingsForm.elements.baseUrl.value,aiSettingsForm.elements.visionModel.value,aiSettingsForm.elements.textModel.value]=preset;
  updateAiProviderHint();
});
const compactTokens=value => value>=1_000_000?`${(value/1_000_000).toFixed(2)}M`:value>=1_000?`${(value/1_000).toFixed(1)}K`:String(value||0);
async function loadAiUsage(){
  const response=await fetch('/api/ai-settings/usage'); if(!response.ok)return;
  const value=await response.json(); const put=(name,text)=>{const target=document.querySelector(`[data-usage="${name}"]`);if(target)target.textContent=text;};
  put('turnInput',compactTokens(value.turnInput)); put('turnOutput',compactTokens(value.turnOutput)); put('session',compactTokens(value.sessionInput+value.sessionOutput));
  put('cached',compactTokens(value.sessionCached)); put('cachePercent',`${Number(value.cachePercent||0).toFixed(1)}%`);
  put('turnCost',`$${Number(value.turnCost||0).toFixed(4)}`); put('todayCost',`$${Number(value.todayCost||0).toFixed(4)}`); put('model',value.model||'尚未调用');
}
loadAiUsage(); setInterval(()=>{if(!document.hidden)loadAiUsage();},15000);
aiSettingsForm?.addEventListener('submit', async event => {
  event.preventDefault(); aiKeyState.textContent='正在保存并测试连接…';
  const body=Object.fromEntries(new FormData(aiSettingsForm).entries());
  for(const name of ['inputPricePerMillion','outputPricePerMillion','cachedInputPricePerMillion']) body[name]=Number(body[name]||0);
  const response=await fetch('/api/ai-settings',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  if(!response.ok){aiKeyState.textContent=`保存失败：${await response.text()}`;return;}
  const value=await response.json(); aiSettingsForm.elements.apiKey.value='';
  try {
    const testResponse=await fetch('/api/ai-settings/test',{method:'POST'});
    if(!testResponse.ok) throw await readApiError(testResponse);
    const test=await testResponse.json();
    aiKeyState.textContent=`连接成功：${test.model}；配置已自动保存。`;
    await loadAiUsage();
  } catch(error) {
    aiKeyState.textContent=`配置已保存，但连接测试失败：${error.message}`;
  }
});
loadAiSettings().catch(error=>{if(aiKeyState)aiKeyState.textContent=`配置读取失败：${error.message}`;});
loadLocalModels().catch(error=>{if(modelServiceState)modelServiceState.textContent=`模型状态读取失败：${error.message}`;});
const taskForm = document.querySelector('#task-form');
const editingScopeSelect = taskForm?.elements.editingScope;
const targetDurationField = taskForm?.querySelector('[data-target-duration]');
function syncTargetDurationVisibility() {
  if (!editingScopeSelect || !targetDurationField) return;
  const highlightsOnly = editingScopeSelect.value === 'HIGHLIGHTS';
  targetDurationField.hidden = !highlightsOnly;
}
editingScopeSelect?.addEventListener('change', syncTargetDurationVisibility);
syncTargetDurationVisibility();
const message = document.querySelector('#form-message');
const detailDialog = document.querySelector('#task-detail-dialog');
const detailTitle = document.querySelector('#detail-title');
const detailContent = document.querySelector('#detail-content');
const storyboardDialog = document.querySelector('#storyboard-dialog');
const storyboardWorkspace = document.querySelector('#storyboard-workspace');
let activeTaskId = null;
let effectPresets = [];
let storyboardProgressTimer = null;
const segmentSearchForm = document.querySelector('#segment-search-form');
const segmentSearchMessage = document.querySelector('#segment-search-message');
const segmentSearchResults = document.querySelector('#segment-search-results');
const segmentImageSearchForm = document.querySelector('#segment-image-search-form');

const stageNames = {
  VIDEO_INGESTION: '素材读取',
  SCENE_DETECTION: '镜头检测与音频提取',
  TRANSCRIPTION: '语音转写',
  VIDEO_UNDERSTANDING: '画面理解',
  HIGHLIGHT_SELECTION: '完整分镜与高光标注',
  SCRIPT_GENERATION: '文案生成',
  VOICE_GENERATION: 'AI 配音',
  TIMELINE_PLANNING: '时间轴规划',
  RENDERING: '视频合成'
};

async function loadTasks() {
  return window.gameNarratorTasks.refresh();
}

async function loadTaskTrash() {
  const list = document.querySelector('#task-trash-list');
  const tasks = await requestJson('/api/tasks/trash');
  list.innerHTML = tasks.length ? tasks.map(task => `<article class="task-card" data-trash-id="${task.id}">
    <div class="task-head"><strong>${escapeHtml(task.name)}</strong><span>${escapeHtml(task.status)}</span></div>
    <div class="tags"><i>删除于 ${escapeHtml(formatDate(task.deletedAt))}</i></div>
    <div class="task-card-actions"><button type="button" data-restore-task="${task.id}">恢复</button>
    <button type="button" class="task-card-delete" data-purge-task="${task.id}" data-task-name="${escapeHtml(task.name)}">永久删除</button></div>
  </article>`).join('') : '<p class="empty">回收站为空</p>';
}

document.querySelector('#task-trash-open')?.addEventListener('click', async () => {
  const dialog = document.querySelector('#task-trash-dialog'); dialog.showModal();
  try { await loadTaskTrash(); } catch (error) { document.querySelector('#task-trash-list').innerHTML=`<p class="empty">${escapeHtml(error.message)}</p>`; }
});
document.querySelector('[data-close-task-trash]')?.addEventListener('click', () => document.querySelector('#task-trash-dialog').close());
document.querySelector('#task-trash-list')?.addEventListener('click', async event => {
  const restore = event.target.closest('[data-restore-task]');
  const purge = event.target.closest('[data-purge-task]');
  const action = restore || purge;
  if (!action || action.disabled) return;
  try {
    if (restore && !window.confirm('确认恢复这个任务并重新显示在任务列表中吗？')) return;
    if (purge) {
      if (!window.confirm(`永久删除“${purge.dataset.taskName}”及全部关联文件？此操作无法恢复。`)) return;
    }
    const originalText = action.textContent;
    action.closest('.task-card')?.querySelectorAll('button').forEach(button => { button.disabled = true; });
    action.textContent = restore ? '正在恢复…' : '正在清理文件…';
    if (restore) await requestJson(`/api/tasks/trash/${restore.dataset.restoreTask}/restore`, {method:'POST'});
    if (purge) {
      const response = await fetch(`/api/tasks/trash/${purge.dataset.purgeTask}`, {method:'DELETE'});
      if (!response.ok) throw await readApiError(response);
    }
    await Promise.all([loadTaskTrash(), loadTasks()]);
  } catch (error) {
    action.closest('.task-card')?.querySelectorAll('button').forEach(button => { button.disabled = false; });
    action.textContent = restore ? '恢复' : '永久删除';
    window.alert(error.message);
  }
});

function voiceProgressText(task, stage) {
  const total = Math.max(1, task.generatedScriptSegmentCount || 1);
  const completed = Math.min(total, Math.max(0, Math.floor((Math.max(10, stage.progress) - 10) / 85 * total)));
  return `配音进度：约 ${completed} / ${total} 段 · ${stage.progress}%（逐段生成后自动进入合成）`;
}

function renderingProgressText(task, stage) {
  const total = Math.max(1, task.generatedScriptSegmentCount || task.selectedHighlightCount || 1);
  const encodedFraction = Math.max(0, Math.min(1, (stage.progress - 10) / 65));
  const current = Math.min(total, Math.max(1, Math.floor(encodedFraction * total) + 1));
  return stage.progress >= 75
    ? `片段 ${total}/${total} 已编码 · 正在合成成片 · ${stage.progress}%`
    : `正在编码片段 ${current}/${total} · ${stage.progress}%`;
}

function stageSubprogressText(stage) {
  if (!stage.subprogressUnit) return '';
  const labels = {CLIP:'片段', FRAME:'画面帧', MODEL:'模型', RENDER:'成片合成'};
  const count = stage.subprogressTotal > 0 ? ` ${stage.subprogressCurrent}/${stage.subprogressTotal}` : '';
  return `${labels[stage.subprogressUnit] || stage.subprogressUnit}${count}${stage.subprogressDetail ? ` · ${stage.subprogressDetail}` : ''}`;
}

function missingToolGuidance(task) {
  const reason = task.stages.find(stage => stage.status === 'PENDING' && stage.errorMessage)?.errorMessage || '';
  if (/whisper/i.test(reason)) return '缺少 Whisper：请运行 .\\scripts\\setup-whisper.ps1，完成后任务会自动重试。';
  if (/piper/i.test(reason)) return '缺少 Piper：请运行 .\\scripts\\setup-piper.ps1，完成后任务会自动重试。';
  return '';
}

function stageTitle(stage) {
  return `${stageNames[stage.type]}：${stage.status}${stage.errorMessage ? `；${stage.errorMessage}` : ''}`;
}

function mediaMetadataText(task) {
  const sceneText = task.detectedSceneCount == null ? '' : ` · ${task.detectedSceneCount} 个转场`;
  const audioText = task.audioCodec === 'none' ? '无音轨' : task.audioCodec;
  return `${formatDuration(task.durationSeconds)} · ${task.videoWidth}×${task.videoHeight} · ${task.framesPerSecond}fps · ${task.videoCodec} · ${audioText}${sceneText}`;
}

taskForm.addEventListener('submit', async event => {
  event.preventDefault();
  message.textContent = '正在上传并建立任务…';
  const requestBody = new FormData(taskForm);
  const booleanOptions = ['storyboardReviewEnabled', 'automaticGenerationEnabled', 'cloudVisionEnabled',
    'aiScriptEnabled', 'aiVoiceEnabled', 'autoAssetsEnabled'];
  booleanOptions.forEach(name => requestBody.set(name, String(Boolean(taskForm.elements[name]?.checked))));
  const video = requestBody.get('video');
  console.info('[GameNarrator] 创建任务', {
    name: requestBody.get('name'),
    gameCategory: requestBody.get('gameCategory'),
    commentaryStyle: requestBody.get('commentaryStyle'),
    targetDurationSeconds: requestBody.get('targetDurationSeconds'),
    videoName: video?.name,
    videoSize: video?.size,
    videoType: video?.type
  });
  try {
    const createdTask = await createTaskWithProgress(requestBody);
    console.info('[GameNarrator] 任务创建成功', createdTask);
    message.textContent = `任务创建成功：${createdTask.id}。处理引擎已自动启动。`;
    const selectedOptions = Object.fromEntries(booleanOptions.map(name => [name, taskForm.elements[name]?.checked]));
    taskForm.reset();
    booleanOptions.forEach(name => { if (taskForm.elements[name]) taskForm.elements[name].checked = selectedOptions[name]; });
    await loadTasks();
  } catch (error) {
    console.error('[GameNarrator] 任务创建失败', error);
    message.textContent = error.message || '创建失败';
  }
});

document.querySelector('#refresh').addEventListener('click', () => {
  loadTasks().catch(showLoadError);
});

segmentSearchForm?.addEventListener('submit', async event => {
  event.preventDefault();
  const data = new FormData(segmentSearchForm);
  const query = String(data.get('query') || '').trim();
  const limit = String(data.get('limit') || '12');
  segmentSearchMessage.textContent = '正在使用 BGE-M3 搜索本地视频镜头…';
  segmentSearchResults.innerHTML = '';
  try {
    const results = await requestJson(`/api/video-segments/search?query=${encodeURIComponent(query)}&limit=${encodeURIComponent(limit)}`);
    segmentSearchMessage.textContent = results.length ? `找到 ${results.length} 个语义相关镜头。` : '没有找到匹配镜头；请先完成至少一个视频的 AI 内容分析。';
    segmentSearchResults.innerHTML = renderSegmentResults(results, '语义');
  } catch (error) {
    segmentSearchMessage.textContent = error.message;
  }
});

function renderSegmentResults(results, scoreLabel) {
  return results.map(item => `
    <article class="segment-search-card" data-segment-result data-task-id="${item.taskId}" data-start-seconds="${item.timestampSeconds}">
      <img src="/api/video-segments/${item.taskId}/${item.frameIndex}/thumbnail" alt="${escapeHtml(item.description)}" loading="lazy">
      <div><header><strong>${escapeHtml(item.taskName)}</strong><span>${escapeHtml(scoreLabel)} ${(item.similarity * 100).toFixed(1)}%</span></header>
      <p>${escapeHtml(item.description)}</p><small>${formatDuration(item.timestampSeconds)} · ${escapeHtml(item.eventType || '其他')}</small>
      <div class="segment-clip-controls">
        <label>剪切时长（秒）<input name="clipDuration" type="number" min="1" max="600" step="1" value="10" list="segment-duration-options"></label>
        <label class="segment-mute"><input name="clipMute" type="checkbox"> 静音</label>
        <button type="button" data-preview-segment>在线预览</button>
        <button type="button" data-download-segment>高清剪切</button>
      </div>
      <video class="segment-clip-player" controls playsinline preload="metadata" hidden></video>
      <p class="segment-clip-message" aria-live="polite"></p>
      <button type="button" data-open-segment-task="${item.taskId}">查看所属任务与分镜</button></div>
    </article>`).join('');
}

segmentImageSearchForm?.addEventListener('submit', async event => {
  event.preventDefault();
  const formData = new FormData(segmentImageSearchForm);
  segmentSearchMessage.textContent = '正在本地比较截图与已分析镜头…';
  segmentSearchResults.innerHTML = '';
  try {
    const response = await fetch('/api/video-segments/search-image?limit=12', {method: 'POST', body: formData});
    if (!response.ok) throw new Error((await response.json().catch(() => ({}))).message || `截图搜索失败（${response.status}）`);
    const results = await response.json();
    segmentSearchMessage.textContent = results.length ? `找到 ${results.length} 个画面相近镜头；分数表示构图相似度。` : '没有可比较的镜头，请先完成视频 AI 分析。';
    segmentSearchResults.innerHTML = renderSegmentResults(results, '构图');
  } catch (error) {
    segmentSearchMessage.textContent = error.message;
  }
});

segmentSearchResults?.addEventListener('click', async event => {
  const button = event.target.closest('[data-open-segment-task]');
  if (button) {
    openTaskDetails(button.dataset.openSegmentTask);
    return;
  }
  const clipButton = event.target.closest('[data-preview-segment],[data-download-segment]');
  if (!clipButton) return;
  const card = clipButton.closest('[data-segment-result]');
  const duration = Number(card.querySelector('[name="clipDuration"]').value);
  const mute = card.querySelector('[name="clipMute"]').checked;
  const status = card.querySelector('.segment-clip-message');
  if (!Number.isFinite(duration) || duration < 1 || duration > 600) {
    status.textContent = '剪切时长必须在 1 到 600 秒之间。';
    return;
  }
  const params = new URLSearchParams({startSeconds:card.dataset.startSeconds,
    durationSeconds:String(duration), mute:String(mute),
    download:String(clipButton.hasAttribute('data-download-segment'))});
  const url = `/api/video-segments/${card.dataset.taskId}/clip?${params}`;
  clipButton.disabled = true;
  status.textContent = clipButton.hasAttribute('data-download-segment') ? '正在生成高清片段…' : '正在生成预览片段…';
  try {
    const response = await fetch(url);
    if (!response.ok) throw await readApiError(response);
    const blobUrl = URL.createObjectURL(await response.blob());
    if (clipButton.hasAttribute('data-download-segment')) {
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = `${card.dataset.taskId}-${Math.round(Number(card.dataset.startSeconds))}s.mp4`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
      status.textContent = '高清片段已生成并开始下载。';
    } else {
      const player = card.querySelector('.segment-clip-player');
      if (player.dataset.objectUrl) URL.revokeObjectURL(player.dataset.objectUrl);
      player.dataset.objectUrl = blobUrl;
      player.src = blobUrl;
      player.hidden = false;
      await player.play().catch(() => {});
      status.textContent = '预览片段已就绪。';
    }
  } catch (error) {
    status.textContent = error.message;
  } finally {
    clipButton.disabled = false;
  }
});

taskList.addEventListener('click', event => {
  const renameButton = event.target.closest('[data-rename-list-task]');
  if (renameButton) {
    event.stopPropagation();
    renameTask(renameButton.dataset.renameListTask, renameButton.dataset.taskName);
    return;
  }
  const deleteButton = event.target.closest('[data-delete-list-task]');
  if (deleteButton) {
    event.stopPropagation();
    deleteTaskFromList(deleteButton);
    return;
  }
  const card = event.target.closest('[data-task-id]');
  if (card) openTaskDetails(card.dataset.taskId);
});

activeTaskPanel.addEventListener('click', event => {
  const cancel = event.target.closest('[data-cancel-task]');
  if (cancel) { cancelTask(cancel); return; }
  const card = event.target.closest('[data-open-active-task]');
  if (card) openTaskDetails(card.dataset.openActiveTask);
});

async function cancelTask(button) {
  if (!window.confirm('确定取消当前任务吗？正在运行的转写、配音或渲染进程会被终止。')) return;
  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = '正在终止进程…';
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 8000);
  try {
    await requestJson(`/api/tasks/${button.dataset.cancelTask}/cancel`, {method:'POST', signal:controller.signal});
    await loadTasks();
    if (activeTaskId === button.dataset.cancelTask) await refreshTaskDetails(activeTaskId);
  } catch (error) {
    button.disabled = false;
    button.textContent = originalText;
    if (error.name === 'AbortError') {
      await loadTasks().catch(() => {});
      window.alert('终止请求等待超时，后台仍会继续终止进程；任务状态已刷新，请稍后再次查看。');
    } else window.alert(`取消失败：${error.message}`);
  } finally {
    window.clearTimeout(timeout);
  }
}

async function renameTask(taskId, currentName) {
  const name = window.prompt('请输入新的任务名称', currentName);
  if (name == null || name.trim() === currentName || !name.trim()) return;
  try {
    await requestJson(`/api/tasks/${taskId}/name`, {
      method: 'PATCH', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({name: name.trim()})
    });
    await loadTasks();
    if (activeTaskId === taskId) await refreshTaskDetails(taskId);
  } catch (error) {
    window.alert(`重命名失败：${error.message}`);
  }
}

async function deleteTaskFromList(button) {
  if (!window.confirm(`将任务“${button.dataset.taskName}”移入回收站吗？项目记录和文件会保留，可稍后恢复。`)) return;
  button.disabled = true; button.textContent = '正在移入…';
  try {
    const response = await fetch(`/api/tasks/${button.dataset.deleteListTask}`, {method:'DELETE'});
    if (!response.ok) throw await readApiError(response);
    button.closest('.task-card')?.remove(); await loadTasks();
  } catch (error) { button.disabled=false; button.textContent='移入回收站'; button.title=error.message; }
}

taskList.addEventListener('keydown', event => {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  const card = event.target.closest('[data-task-id]');
  if (!card) return;
  event.preventDefault();
  openTaskDetails(card.dataset.taskId);
});

document.querySelector('#detail-close').addEventListener('click', () => detailDialog.close());
detailDialog.addEventListener('click', event => {
  if (event.target === detailDialog) detailDialog.close();
});
detailDialog.addEventListener('close', () => { activeTaskId = null; });
detailContent.addEventListener('click', async event => {
  const diagnosticsButton = event.target.closest('[data-open-error-diagnostics]');
  if (diagnosticsButton) {
    const openButton = document.querySelector('#diagnostics-open');
    if (openButton) openButton.dataset.taskId = diagnosticsButton.dataset.taskId || activeTaskId || '';
    openButton?.click();
    return;
  }
  const retryButton = event.target.closest('[data-retry-task]');
  const addAssetButton = event.target.closest('[data-add-project-asset]');
  const enhancementButton = event.target.closest('[data-task-enhancement]');
  if (enhancementButton) {
    const taskId = enhancementButton.dataset.taskId;
    const type = enhancementButton.dataset.taskEnhancement;
    const status = detailContent.querySelector('[data-enhancement-status]');
    enhancementButton.disabled = true;
    status.textContent = type === 'transcription' ? '正在启动语音识别…'
      : type === 'assets' ? '正在匹配并下载素材…' : '正在启动自动特效渲染…';
    try {
      const response = await fetch(`/api/tasks/${taskId}/enhancements/${type}`, {method:'POST'});
      if (!response.ok) throw await readApiError(response);
      if (type === 'transcription') {
        status.textContent = '转写在后台运行，当前剪辑不会被锁定或覆盖。';
        pollEnhancementStatus(taskId);
      } else if (type === 'assets') {
        const result = await response.json();
        status.textContent = `自动素材完成：新增 ${result.assignedCount} 个；${result.warnings?.join('；') || '可进入分镜工作台调整。'}`;
        enhancementButton.disabled = false;
      } else {
        status.textContent = '自动特效渲染已启动；现有剪辑数据保持不变。';
        setTimeout(() => refreshTaskDetails(taskId), 1200);
      }
    } catch (error) {
      enhancementButton.disabled = false;
      status.textContent = `增强失败：${error.message}`;
    }
    return;
  }
  if (addAssetButton) {
    addAssetButton.disabled = true;
    addAssetButton.textContent = '正在加入…';
    try {
      await requestJson(`/api/assets/projects/${addAssetButton.dataset.addProjectAsset}`, {method:'POST'});
      addAssetButton.textContent = '已加入素材库';
    } catch (error) {
      addAssetButton.disabled = false;
      addAssetButton.textContent = '加入素材库';
      addAssetButton.title = error.message;
    }
    return;
  }
  if (!retryButton) return;
  retryButton.disabled = true;
  retryButton.textContent = '正在重新启动…';
  try {
    const response = await fetch(`/api/tasks/${retryButton.dataset.retryTask}/retry`, {
      method: 'POST'
    });
    if (!response.ok) throw await readApiError(response);
    await Promise.all([loadTasks(), refreshTaskDetails(retryButton.dataset.retryTask)]);
  } catch (error) {
    retryButton.disabled = false;
    retryButton.textContent = '重试失败阶段';
    retryButton.title = error.message;
  }
});
detailContent.addEventListener('change', event => {
  if (!event.target.matches('[name="voiceProfile"]')) return;
  const card = event.target.closest('[data-script-segment]');
  const option = event.target.selectedOptions[0];
  if (!card || !option) return;
  card.querySelector('[name="voiceId"]').value = option.dataset.voiceId || '';
  card.querySelector('[name="voiceEmotion"]').value = option.dataset.emotion || 'NEUTRAL';
  card.querySelector('[name="voiceSpeed"]').value = option.dataset.speed || '1';
  card.querySelector('[name="voicePitch"]').value = option.dataset.pitch || '0';
});

detailContent.addEventListener('input', event => {
  if (event.target.matches('[name="voiceSpeed"]')) {
    event.target.closest('label')?.querySelector('output').replaceChildren(`${Number(event.target.value).toFixed(2)}×`);
  } else if (event.target.matches('[name="intensity"]')) {
    event.target.closest('label')?.querySelector('output').replaceChildren(`${Math.round(Number(event.target.value) * 100)}%`);
  } else if (event.target.matches('[data-color-control]')) {
    event.target.closest('label')?.querySelector('output').replaceChildren(Number(event.target.value).toFixed(2));
  }
});
detailContent.addEventListener('change', event => {
  if (!event.target.matches('[name="presetCode"]')) return;
  const preset=effectPresets.find(item=>item.code===event.target.value);
  const panel=event.target.closest('[data-effect-settings]');
  if(!preset||!panel)return;
  panel.querySelector('[name="intensity"]').value=String(preset.defaultIntensity);
  panel.querySelector('[data-effect-intensity]').textContent=`${Math.round(preset.defaultIntensity*100)}%`;
  panel.querySelector('[data-effect-preset-details]').innerHTML=effectPresetDetails(preset);
});
detailContent.addEventListener('submit', async event => {
  const form = event.target.closest('[data-effect-settings]');
  if (!form) return;
  event.preventDefault();
  const button = form.querySelector('button[type="submit"]');
  const status = form.querySelector('.effect-message');
  const values = new FormData(form);
  button.disabled = true;
  button.textContent = '正在启动渲染…';
  try {
    const response = await fetch(`/api/tasks/${form.dataset.effectSettings}/rerender-effects`, {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
        presetCode:String(values.get('presetCode')),
        intensity:Number(values.get('intensity')),
        dynamicSubtitles:values.get('dynamicSubtitles') === 'on',
        keywordHighlights:values.get('keywordHighlights') === 'on',
        burnSubtitles:values.get('burnSubtitles') === 'on',
        subtitleTemplate:String(values.get('subtitleTemplate')),
        soundEffects:values.get('soundEffects') === 'on',
        brightness:Number(values.get('brightness')),
        contrast:Number(values.get('contrast')),
        saturation:Number(values.get('saturation')),
        temperature:Number(values.get('temperature')),
        useLut:values.get('useLut') === 'on'
      })
    });
    if (!response.ok) throw await readApiError(response);
    status.textContent = '特效重渲染已启动，可在处理流水线查看进度。';
    button.textContent = '渲染进行中';
    setTimeout(() => refreshTaskDetails(form.dataset.effectSettings), 1200);
  } catch (error) {
    button.disabled = false;
    button.textContent = '应用特效并重新渲染';
    status.textContent = error.message;
  }
});
detailContent.addEventListener('click', async event => {
  const upload = event.target.closest('[data-upload-color-lut]');
  const clear = event.target.closest('[data-clear-color-lut]');
  if (!upload && !clear) return;
  const form = event.target.closest('[data-effect-settings]');
  const status = form.querySelector('[data-color-lut-status]');
  const taskId = form.dataset.effectSettings;
  event.target.disabled = true;
  try {
    if (upload) {
      const file = form.querySelector('[name="lutFile"]').files?.[0];
      if (!file) throw new Error('请先选择 .cube LUT 文件');
      const body = new FormData(); body.append('file', file);
      const response = await fetch(`/api/tasks/${taskId}/color-lut`, {method:'POST', body});
      if (!response.ok) throw await readApiError(response);
      const value = await response.json();
      form.querySelector('[name="useLut"]').checked = true;
      status.textContent = `已上传 ${value.originalName} · ${value.dimension}×${value.dimension}×${value.dimension}`;
    } else {
      const response = await fetch(`/api/tasks/${taskId}/color-lut`, {method:'DELETE'});
      if (!response.ok) throw await readApiError(response);
      form.querySelector('[name="useLut"]').checked = false;
      form.querySelector('[name="lutFile"]').value = '';
      status.textContent = '尚未上传 LUT';
    }
  } catch (error) { status.textContent = error.message; }
  finally { event.target.disabled = false; }
});
detailContent.addEventListener('click', async event => {
  const storyboardButton = event.target.closest('[data-open-storyboard]');
  if (storyboardButton) {
    await loadStoryboardEditor(storyboardButton.dataset.openStoryboard);
    return;
  }
  const storyboardAction = event.target.closest('[data-storyboard-action]');
  if (storyboardAction) {
    await handleStoryboardAction(storyboardAction);
    return;
  }
  const openButton = event.target.closest('[data-open-script]');
  if (openButton) {
    await loadScriptEditor(openButton.dataset.openScript);
    return;
  }
  const actionButton = event.target.closest('[data-script-action]');
  if (!actionButton) return;
  const card = actionButton.closest('[data-script-segment]');
  const taskId = actionButton.dataset.taskId;
  const clipIndex = actionButton.dataset.clipIndex;
  actionButton.disabled = true;
  try {
    if (actionButton.dataset.scriptAction === 'quality-review') {
      actionButton.textContent = 'AI 正在独立评审…';
      await requestJson(`/api/tasks/${taskId}/script/quality-review`, {method: 'POST'});
    } else if (actionButton.dataset.scriptAction === 'save') {
      const payload = {
        narration: card.querySelector('[name="narration"]').value,
        subtitle: card.querySelector('[name="subtitle"]').value,
        effectCue: card.querySelector('[name="effectCue"]').value
      };
      await requestJson(`/api/tasks/${taskId}/script/segments/${clipIndex}`, {
        method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
      });
    } else if (actionButton.dataset.scriptAction === 'regenerate') {
      const instruction = card.querySelector('[name="instruction"]').value;
      await requestJson(`/api/tasks/${taskId}/script/segments/${clipIndex}/regenerate`, {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({instruction})
      });
    } else if (actionButton.dataset.scriptAction === 'voice') {
      await requestJson(`/api/tasks/${taskId}/voice/segments/${clipIndex}/regenerate`, {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          voiceId: card.querySelector('[name="voiceId"]').value,
          speed: Number(card.querySelector('[name="voiceSpeed"]').value),
          profileId: card.querySelector('[name="voiceProfile"]').value,
          emotion: card.querySelector('[name="voiceEmotion"]').value,
          pitchSemitones: Number(card.querySelector('[name="voicePitch"]').value)
        })
      });
    } else if (actionButton.dataset.scriptAction === 'voice-preview') {
      const response = await fetch(`/api/tasks/${taskId}/voice/preview`, {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({text: card.querySelector('[name="narration"]').value, settings: {
          voiceId: card.querySelector('[name="voiceId"]').value,
          speed: Number(card.querySelector('[name="voiceSpeed"]').value),
          profileId: card.querySelector('[name="voiceProfile"]').value,
          emotion: card.querySelector('[name="voiceEmotion"]').value,
          pitchSemitones: Number(card.querySelector('[name="voicePitch"]').value)
        }})
      });
      if (!response.ok) throw await readApiError(response);
      const audio = card.querySelector('[data-voice-preview]');
      if (audio.src?.startsWith('blob:')) URL.revokeObjectURL(audio.src);
      audio.src = URL.createObjectURL(await response.blob());
      audio.hidden = false;
      await audio.play();
    } else if (actionButton.dataset.scriptAction.startsWith('review-')) {
      const status = actionButton.dataset.scriptAction === 'review-approved' ? 'APPROVED' : 'NEEDS_CHANGES';
      await requestJson(`/api/tasks/${taskId}/script/segments/${clipIndex}/review`, {
        method: 'PUT', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({status, note: card.querySelector('[name="reviewNote"]').value})
      });
    }
    await refreshTaskDetails(taskId);
    await loadScriptEditor(taskId);
  } catch (error) {
    actionButton.disabled = false;
    actionButton.title = error.message;
  }
});

async function openTaskDetails(taskId) {
  activeTaskId = taskId;
  detailTitle.textContent = '任务详情';
  detailContent.innerHTML = '<p class="empty">正在读取任务详情…</p>';
  if (!detailDialog.open) detailDialog.showModal();
  await loadLazyScript('export').catch(error => console.warn(error.message));
  await refreshTaskDetails(taskId);
}

async function refreshTaskDetails(taskId) {
  try {
    if (!effectPresets.length) {
      const presetResponse = await fetch('/api/effect-presets');
      if (presetResponse.ok) effectPresets = await presetResponse.json();
    }
    const response = await fetch(`/api/tasks/${taskId}`);
    if (!response.ok) throw await readApiError(response);
    const task = await response.json();
    if (activeTaskId === taskId) renderTaskDetails(task);
  } catch (error) {
    if (activeTaskId === taskId) {
      detailContent.innerHTML = `<div class="task-error">详情加载失败：${escapeHtml(error.message)}</div>`;
    }
  }
}

function renderTaskDetails(task) {
  detailTitle.textContent = task.name;
  const completedCount = task.stages.filter(stage => stage.status === 'COMPLETED').length;
  const overallProgress = Math.round(task.stages.reduce((sum, stage) => sum + stage.progress, 0) / task.stages.length);
  detailContent.innerHTML = `
    <section class="detail-block task-action-center">
      <header><div><small>下一步</small><h3>任务操作中心</h3></div><span>${task.status}</span></header>
      <div class="task-primary-actions">
        ${task.status === 'PROCESSING' ? `<button type="button" class="danger-button" data-cancel-task="${task.id}">终止正在运行的任务</button>` : ''}
        ${['FAILED','CANCELLED'].includes(task.status) ? `<button type="button" data-retry-task="${task.id}">${task.status === 'CANCELLED' ? '从中断处继续制作' : '重试失败步骤'}</button>` : ''}
        ${task.generatedScriptPath ? `<button type="button" data-open-storyboard="${task.id}">打开分镜与时间线</button><button type="button" class="secondary-button" data-open-script="${task.id}">编辑解说文案</button>` : ''}
      </div>
      <p>${task.status === 'PROCESSING' ? '任务正在后台制作；可以关闭此窗口，进度不会丢失。需要停止时使用上方红色按钮。' : task.generatedScriptPath ? '建议先打开分镜与时间线检查画面；只改文字或单段配音时进入“编辑解说文案”。' : '这里会根据任务进度显示当前最合适的下一步操作。'}</p>
      <details class="task-more-actions"><summary>更多管理操作</summary><div><button type="button" class="secondary-button" data-rename-task="${task.id}" data-task-name="${escapeHtml(task.name)}">修改任务名称</button><button type="button" class="text-danger-button" data-delete-task="${task.id}" data-task-name="${escapeHtml(task.name)}">移入回收站</button></div><small>移入回收站后仍可恢复；只有在回收站中永久删除才会清理关联文件。</small></details>
    </section>
    <section class="detail-block enhancement-toolbox">
      <h3>智能增强工具箱</h3>
      <p class="visual-summary">手动剪辑始终可用；下面每项可随时单独执行，失败不会破坏当前时间线或已有成片。</p>
      <div class="task-operations">
        <button type="button" data-task-enhancement="transcription" data-task-id="${task.id}" ${task.status === 'PROCESSING' || !task.durationSeconds ? 'disabled' : ''}>重新转写并生成字幕</button>
        <button type="button" data-task-enhancement="assets" data-task-id="${task.id}" ${task.status === 'PROCESSING' || !task.generatedScriptPath ? 'disabled' : ''}>自动匹配素材</button>
        <button type="button" data-task-enhancement="effects" data-task-id="${task.id}" ${task.status === 'PROCESSING' || !task.timelinePath ? 'disabled' : ''}>自动规划特效并渲染</button>
      </div>
      <small data-enhancement-status>${task.status === 'PROCESSING' ? '主流水线运行期间暂不可启动独立增强。' : '可按需使用，不要求创建任务时预先开启 AI。'}</small>
    </section>
    <section class="detail-summary">
      <div class="detail-status ${task.status.toLowerCase()}">${task.status}</div>
      <div><span>总体进度</span><strong>${overallProgress}%</strong></div>
      <div><span>已完成阶段</span><strong>${completedCount} / ${task.stages.length}</strong></div>
      <div><span>创建时间</span><strong>${formatDate(task.createdAt)}</strong></div>
    </section>
    <section class="detail-block">
      <h3>任务配置</h3>
      <dl class="detail-grid">
        <div><dt>内容类别</dt><dd>${escapeHtml(task.gameCategory)}</dd></div>
        <div><dt>解说风格</dt><dd>${escapeHtml(task.commentaryStyle)}</dd></div>
        <div><dt>目标时长</dt><dd>${task.targetDurationSeconds} 秒</dd></div>
        <div><dt>素材参数</dt><dd>${task.durationSeconds ? `${formatDuration(task.durationSeconds)} / ${task.videoWidth}×${task.videoHeight} / ${task.framesPerSecond}fps` : '等待读取'}</dd></div>
      </dl>
      <p class="detail-brief">${escapeHtml(task.taskBrief)}</p>
    </section>
    ${task.failureReason ? `<section class="detail-block"><h3>失败原因</h3>${taskFailureHtml(task.failureReason)}<div class="task-operations"><button type="button" data-open-error-diagnostics data-task-id="${task.id}">查看此任务完整日志</button></div></section>` : ''}
    <section class="detail-block">
      <h3>处理流水线</h3>
      <div class="stage-details">${task.stages.map(stage => `
        <article class="stage-row ${stage.status.toLowerCase()}">
          <span class="stage-index">${String(stage.sequence).padStart(2, '0')}</span>
          <div class="stage-info"><strong>${stageNames[stage.type]}</strong><small>${stage.status}${stageSubprogressText(stage) ? ` · ${escapeHtml(stageSubprogressText(stage))}` : ''}${stage.errorMessage ? ` · ${escapeHtml(stage.errorMessage)}` : ''}</small></div>
          <div class="stage-progress"><i style="width:${stage.progress}%"></i></div>
          <b>${stage.progress}%</b>
        </article>`).join('')}
      </div>
    </section>
    ${artifactSection(task)}
    ${task.visualSummary ? `<section class="detail-block"><h3>AI 视频内容分析</h3><p class="visual-summary">${escapeHtml(task.visualSummary)}</p><div class="tags"><i>已分析 ${task.analyzedFrameCount} 个镜头，并用于高光筛选</i></div></section>` : ''}
    ${task.highlightSummary ? `<section class="detail-block"><h3>完整分镜与高光标注</h3><p class="visual-summary">${escapeHtml(task.highlightSummary)}</p><div class="tags"><i>已生成 ${task.selectedHighlightCount} 个连续片段</i></div></section>` : ''}
    ${task.generatedNarration ? `<section class="detail-block"><h3>${escapeHtml(task.generatedTitle || 'AI 文案')}</h3><p class="visual-summary">${escapeHtml(task.scriptSynopsis)}</p><pre class="transcript-text">${escapeHtml(task.generatedNarration)}</pre><div class="tags"><i>${task.generatedScriptSegmentCount} 段配音文案</i></div></section>` : ''}
    ${task.generatedVoiceSegmentCount ? `<section class="detail-block"><h3>${task.aiVoiceEnabled ? 'AI 配音' : '音频轨道'}</h3><p class="visual-summary">${task.aiVoiceEnabled ? `Piper 中文音色已生成 ${task.generatedVoiceSegmentCount} 段本地配音。` : `已跳过 AI 配音，并为 ${task.generatedVoiceSegmentCount} 段建立静音占位轨道，可在剪辑器中替换或删除。`}</p></section>` : ''}
    ${task.timelinePath ? `<section class="detail-block"><h3>剪辑时间线</h3><p class="visual-summary">已规划 ${formatDuration(task.plannedOutputDurationSeconds)} 的成片时间线；${task.voiceOverflowCount ? `${task.voiceOverflowCount} 段配音需要在渲染时调整语速。` : '所有配音均可放入对应镜头。'}</p></section>` : ''}
    ${renderPreviewSection(task)}
    ${effectSettingsSection(task)}
    ${renderedVideoSection(task)}
    ${task.transcriptText ? `<section class="detail-block"><h3>语音转写</h3><pre class="transcript-text">${escapeHtml(task.transcriptText)}</pre></section>` : ''}
  `;
  if (task.timelinePath) {
    refreshRenderPreview(task.id);
    refreshColorLutStatus(task.id);
  }
}

async function refreshColorLutStatus(taskId) {
  const form = detailContent.querySelector(`[data-effect-settings="${taskId}"]`);
  if (!form) return;
  const status = form.querySelector('[data-color-lut-status]');
  try {
    const value = await requestJson(`/api/tasks/${taskId}/color-lut`);
    form.querySelector('[name="useLut"]').checked = Boolean(value.configured);
    status.textContent = value.configured
      ? `已配置 ${value.originalName} · ${value.dimension || '?'}³ · ${(Number(value.sizeBytes || 0) / 1024).toFixed(1)} KB`
      : '尚未上传 LUT';
  } catch (error) { status.textContent = `LUT 状态读取失败：${error.message}`; }
}

async function pollEnhancementStatus(taskId) {
  for (let attempt = 0; attempt < 120 && activeTaskId === taskId; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 1000));
    try {
      const jobs = await requestJson(`/api/tasks/${taskId}/enhancements`);
      const transcription = jobs.find(job => job.type === 'TRANSCRIPTION');
      const target = detailContent.querySelector('[data-enhancement-status]');
      if (!target || !transcription) return;
      target.textContent = transcription.message;
      if (transcription.status === 'COMPLETED') {
        await refreshTaskDetails(taskId);
        return;
      }
      if (transcription.status === 'FAILED') {
        detailContent.querySelector('[data-task-enhancement="transcription"]')?.removeAttribute('disabled');
        return;
      }
    } catch (_) { return; }
  }
}

function renderPreviewSection(task) {
  const rendering = task.stages.some(stage => stage.type === 'RENDERING' && ['RUNNING','COMPLETED'].includes(stage.status));
  if (!task.timelinePath || (!rendering && !task.renderedVideoPath)) return '';
  return `<section class="detail-block render-preview-panel" data-render-preview="${task.id}">
    <div class="render-preview-heading"><div><h3>渲染节奏预览</h3><p>片段编码完成后实时出现低分辨率序列帧，可提前检查镜头节奏。</p></div><b data-render-preview-count>正在读取…</b></div>
    <div class="render-preview-track" data-render-preview-track><span class="render-preview-empty">等待首个片段完成编码…</span></div>
  </section>`;
}

async function refreshRenderPreview(taskId) {
  const panel = detailContent.querySelector(`[data-render-preview="${taskId}"]`);
  if (!panel) return;
  try {
    const response = await fetch(`/api/tasks/${taskId}/render-preview`, {cache:'no-store'});
    if (!response.ok) throw await readApiError(response);
    const frames = await response.json();
    const track = panel.querySelector('[data-render-preview-track]');
    panel.querySelector('[data-render-preview-count]').textContent = `${frames.length} 帧`;
    const existing = new Set(Array.from(track.querySelectorAll('[data-preview-index]')).map(item => Number(item.dataset.previewIndex)));
    track.querySelector('.render-preview-empty')?.remove();
    frames.filter(frame => !existing.has(frame.index)).forEach(frame => {
      const item = document.createElement('figure');
      item.dataset.previewIndex = frame.index;
      item.innerHTML = `<img src="${frame.imageUrl}" alt="片段 ${frame.sequence} 渲染预览" loading="lazy"><figcaption><b>${String(frame.sequence).padStart(2,'0')}</b><span>${formatDuration(frame.outputStartSeconds)}–${formatDuration(frame.outputEndSeconds)}</span></figcaption>`;
      track.appendChild(item);
    });
    if (!frames.length && !track.children.length) track.innerHTML = '<span class="render-preview-empty">等待首个片段完成编码…</span>';
  } catch (error) {
    panel.querySelector('[data-render-preview-count]').textContent = '预览暂不可用';
  }
}

function effectSettingsSection(task) {
  if (!task.timelinePath || !effectPresets.length) return '';
  const defaultCode = effectPresets.some(item => item.code === task.commentaryStyle)
    ? task.commentaryStyle : effectPresets[0].code;
  const defaultPreset=effectPresets.find(item=>item.code===defaultCode)||effectPresets[0];
  return `<section class="detail-block effect-settings-panel">
    <h3>剧场特效</h3>
    <form data-effect-settings="${task.id}">
      <div class="grid">
        <label>特效预设<select name="presetCode">${effectPresets.map(item =>
          `<option value="${escapeHtml(item.code)}" ${item.code === defaultCode ? 'selected' : ''}>${escapeHtml(item.name)}</option>`
        ).join('')}</select></label>
        <label>特效强度 <output data-effect-intensity>默认</output>
          <input name="intensity" type="range" min="0" max="1" step="0.05" value="${effectPresets.find(item => item.code === defaultCode)?.defaultIntensity ?? 0.75}">
        </label>
      </div>
      <div class="effect-preset-details" data-effect-preset-details>${effectPresetDetails(defaultPreset)}</div>
      <details class="style-template-market">
        <summary>打开风格模板商店 · ${effectPresets.length} 套</summary>
        <div class="style-template-toolbar"><div><strong>本地风格模板</strong><small>内置模板和导入模板均可一键套用；导出 JSON 后可分享给其他项目。</small></div><label>导入模板 JSON<input type="file" accept="application/json,.json" data-style-template-import></label></div>
        <div class="style-template-grid">${effectPresets.map(item => `<article data-template-code="${escapeHtml(item.code)}"><header><b>${escapeHtml(item.name)}</b><i>${Math.round(item.defaultIntensity * 100)}%</i></header><p>${escapeHtml(item.description)}</p><div>${item.preferredEffects.slice(0,4).map(effect => `<span>${escapeHtml(visualEffectLabels[effect] || effect)}</span>`).join('')}</div><footer><button type="button" data-apply-style-template="${escapeHtml(item.code)}">一键套用</button><a href="/api/effect-presets/${encodeURIComponent(item.code)}/export" download>导出 JSON</a></footer></article>`).join('')}</div>
        <p class="effect-message" data-template-message aria-live="polite"></p>
      </details>
      <div class="grid"><label>字幕模板<select name="subtitleTemplate"><option value="ANIME_OUTLINE">动漫描边</option><option value="IMPACT_RED">高燃冲击</option><option value="COMEDY_POP">喜剧弹跳</option><option value="TYPEWRITER_DARK">暗色打字机</option><option value="CLEAN_WHITE">简洁白字</option></select></label></div>
      <label class="effect-toggle"><input name="dynamicSubtitles" type="checkbox" checked>启用逐字动态高亮</label>
      <label class="effect-toggle"><input name="keywordHighlights" type="checkbox" checked>强调击杀、弹反、胜利、Boss 等关键词</label>
      <label class="effect-toggle"><input name="burnSubtitles" type="checkbox" checked>将包装字幕烧录到画面（关闭时保留软字幕轨和 ASS 文件）</label>
      <label class="effect-toggle"><input name="soundEffects" type="checkbox">加入冲击、转场和喜剧提示音</label>
      <details class="color-grading-panel" open>
        <summary>调色轮与 LUT</summary>
        <div class="grid">
          <label>亮度 <output>0.00</output><input data-color-control name="brightness" type="range" min="-1" max="1" step="0.05" value="0"></label>
          <label>对比度 <output>1.00</output><input data-color-control name="contrast" type="range" min="0" max="3" step="0.05" value="1"></label>
          <label>饱和度 <output>1.00</output><input data-color-control name="saturation" type="range" min="0" max="3" step="0.05" value="1"></label>
          <label>色温 <output>0.00</output><input data-color-control name="temperature" type="range" min="-1" max="1" step="0.05" value="0"></label>
        </div>
        <div class="lut-upload-row"><input name="lutFile" type="file" accept=".cube,text/plain"><button type="button" data-upload-color-lut>上传 LUT</button><button type="button" data-clear-color-lut>清除 LUT</button></div>
        <label class="effect-toggle"><input name="useLut" type="checkbox">渲染时应用已上传的 3D LUT</label>
        <small data-color-lut-status>正在读取 LUT 状态…</small>
      </details>
      <p class="effect-note">参考 Premiere 常见的运动、模糊、颜色、风格化和转场效果；系统只显示当前 FFmpeg 渲染器能够实际输出的类型。</p>
      <button type="submit">应用特效并重新渲染</button>
      <span class="effect-message" aria-live="polite"></span>
    </form>
  </section>`;
}

const visualEffectLabels={ZOOM_PUNCH:'缩放冲击',CAMERA_SHAKE:'镜头震动',WHITE_FLASH:'闪白',SLOW_MOTION:'慢动作',FREEZE_ACCENT:'定格强调',SPEED_LINES:'速度线',CINEMA_BARS:'电影黑边',TITLE_CARD:'标题卡',GAUSSIAN_BLUR:'高斯模糊',VIGNETTE:'暗角',BLACK_AND_WHITE:'黑白',WARM_TONE:'暖色调',COOL_TONE:'冷色调',HIGH_CONTRAST:'高对比',RGB_SPLIT:'RGB 分离',HORIZONTAL_FLIP:'水平翻转',PIXELATE:'马赛克像素化',LENS_DISTORTION:'镜头畸变'};
const transitionLabels={HARD_CUT:'硬切',FADE:'淡入淡出',DISSOLVE:'叠化',PUSH:'推镜',ANIME_IMPACT:'冲击转场'};
function effectPresetDetails(preset){
  return `<p>${escapeHtml(preset.description)}</p><div>${preset.preferredEffects.map(item=>`<span>${escapeHtml(visualEffectLabels[item]||item)}</span>`).join('')}</div><small>转场：${preset.allowedTransitions.map(item=>escapeHtml(transitionLabels[item]||item)).join(' / ')}</small>`;
}

detailContent.addEventListener('click', async event => {
  const styleButton = event.target.closest('[data-apply-style-template]');
  if (styleButton) {
    const form = styleButton.closest('[data-effect-settings]');
    const select = form?.querySelector('[name="presetCode"]');
    if (select) { select.value = styleButton.dataset.applyStyleTemplate; select.dispatchEvent(new Event('change', {bubbles:true})); }
    form?.querySelector('.style-template-market')?.removeAttribute('open');
    return;
  }
  const cancelButton = event.target.closest('[data-cancel-task]');
  if (cancelButton) { await cancelTask(cancelButton); return; }
  const renameButton = event.target.closest('[data-rename-task]');
  if (renameButton) {
    await renameTask(renameButton.dataset.renameTask, renameButton.dataset.taskName);
    return;
  }
  const button = event.target.closest('[data-delete-task]');
  if (!button) return;
  if (!window.confirm(`确定将任务“${button.dataset.taskName}”移入回收站吗？之后可以恢复。`)) return;
  button.disabled = true;
  button.textContent = '正在删除…';
  try {
    const response = await fetch(`/api/tasks/${button.dataset.deleteTask}`, {method:'DELETE'});
    if (!response.ok) throw await readApiError(response);
    activeTaskId = null;
    detailDialog.close();
    await loadTasks();
  } catch (error) {
    button.disabled = false;
    button.textContent = '移入回收站';
    button.title = error.message;
  }
});

detailContent.addEventListener('change', async event => {
  if (!event.target.matches('[data-style-template-import]')) return;
  const input = event.target;
  const message = input.closest('.style-template-market').querySelector('[data-template-message]');
  try {
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 64 * 1024) throw new Error('模板 JSON 不能超过 64 KB');
    const template = JSON.parse(await file.text());
    const imported = await requestJson('/api/effect-presets/import', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(template)});
    const existing = effectPresets.findIndex(item => item.code === imported.code);
    if (existing >= 0) effectPresets[existing] = imported; else effectPresets.push(imported);
    message.textContent = `已导入“${imported.name}”，正在刷新模板商店…`;
    if (activeTaskId) await refreshTaskDetails(activeTaskId);
  } catch (error) {
    message.textContent = `导入失败：${error.message}`;
    input.value = '';
  }
});

async function loadScriptEditor(taskId) {
  const [script, voices, profiles, manualReviews] = await Promise.all([
    requestJson(`/api/tasks/${taskId}/script`),
    requestJson(`/api/tasks/${taskId}/voice/options`),
    requestJson(`/api/tasks/${taskId}/voice/profiles`),
    requestJson(`/api/tasks/${taskId}/script/reviews`)
  ]);
  const voiceOptions = voices.map(voice => `<option value="${escapeHtml(voice.id)}" ${voice.available ? '' : 'disabled'} ${voice.defaultVoice ? 'selected' : ''}>${escapeHtml(voice.name)}${voice.available ? '' : '（未安装）'}</option>`).join('');
  const profileOptions = profiles.map(profile => `<option value="${escapeHtml(profile.id)}" data-voice-id="${escapeHtml(profile.voiceId)}" data-emotion="${escapeHtml(profile.emotion)}" data-speed="${profile.speed}" data-pitch="${profile.pitchSemitones}" ${profile.available ? '' : 'disabled'} ${profile.defaultProfile ? 'selected' : ''}>${escapeHtml(profile.name)}</option>`).join('');
  const existing = detailContent.querySelector('.script-editor');
  if (existing) existing.remove();
  detailContent.insertAdjacentHTML('beforeend', `
    <section class="detail-block script-editor">
      <h3>分段文案编辑</h3>
      <p class="effect-note">修改文案会使配音、时间线和成片进入待重建状态。单段配音完成后可重新启动任务生成时间线和成片。</p>
      <aside class="script-quality ${script.qualityReview?.passed ? 'passed' : 'needs-work'}">
        <strong>文案质量 ${script.qualityReview?.score ?? 0} / 100</strong>
        <span>${escapeHtml(script.qualityReview?.summary || '尚未生成质量评审')}</span>
        ${script.qualityReview?.issues?.length ? `<ul>${script.qualityReview.issues.map(issue => `<li>${escapeHtml(issue)}</li>`).join('')}</ul>` : ''}
        <button type="button" data-script-action="quality-review" data-task-id="${taskId}">AI 独立复评</button>
      </aside>
      <div class="script-segment-list">${script.segments.map(segment => {
        const manual = manualReviews[String(segment.clipIndex)] || {};
        const duration = Math.max(.1, segment.endSeconds - segment.startSeconds);
        const overflow = segment.narration.length > duration * 5;
        const aiIssues = (script.qualityReview?.issues || []).filter(issue =>
          new RegExp(`(?:片段|分镜|clip)\\s*${segment.clipIndex}\\b`, 'i').test(issue));
        const warnings = [...aiIssues, ...(overflow ? ['文案长度可能超过当前镜头的可配音时长'] : [])];
        return `
        <article class="script-segment-card ${warnings.length ? 'quality-warning' : ''} ${manual.status === 'APPROVED' ? 'manual-approved' : manual.status === 'NEEDS_CHANGES' ? 'manual-needs-changes' : ''}" data-script-segment="${segment.clipIndex}">
          <header><strong>片段 ${segment.clipIndex}</strong><small>${segment.startSeconds.toFixed(1)}s – ${segment.endSeconds.toFixed(1)}s</small></header>
          ${warnings.length ? `<aside class="segment-quality-issues"><strong>需要检查</strong><ul>${warnings.map(issue => `<li>${escapeHtml(issue)}</li>`).join('')}</ul></aside>` : ''}
          <label>解说文案<textarea name="narration" maxlength="500">${escapeHtml(segment.narration)}</textarea></label>
          <label>字幕<input name="subtitle" maxlength="500" value="${escapeHtml(segment.subtitle)}"></label>
          <label>特效提示<input name="effectCue" maxlength="200" value="${escapeHtml(segment.effectCue)}"></label>
          <label>AI 重写要求<input name="instruction" maxlength="500" placeholder="例如：更紧张、更精简，保持事实不变"></label>
          <div class="voice-controls"><label>配音档案<select name="voiceProfile">${profileOptions}</select></label><label>配音音色<select name="voiceId">${voiceOptions}</select></label><label>情绪<select name="voiceEmotion"><option value="NEUTRAL">自然</option><option value="EXCITED">兴奋</option><option value="CALM">沉稳</option><option value="TENSE">紧张</option><option value="SAD">低沉</option></select></label><label>语速<input name="voiceSpeed" type="range" min="0.5" max="2" step="0.05" value="1"><output>1.00×</output></label><label>音高<input name="voicePitch" type="range" min="-6" max="6" step="0.5" value="0"><output>0</output></label></div>
          <audio data-voice-preview controls hidden preload="none"></audio>
          <div class="script-actions">
            <button type="button" data-script-action="save" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">保存并局部配音</button>
            <button type="button" data-script-action="regenerate" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">AI 重写</button>
            <button type="button" data-script-action="voice" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">重新配音</button>
            <button type="button" data-script-action="voice-preview" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">试听档案</button>
            <button type="button" data-script-action="review-approved" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">通过</button>
            <button type="button" data-script-action="review-needs-changes" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">需修改</button>
          </div>
          <label>评审备注<input name="reviewNote" maxlength="500" value="${escapeHtml(manual.note || '')}" placeholder="说明事实冲突、节奏或措辞问题"></label>
        </article>`}).join('')}</div>
    </section>`);
  detailContent.querySelector('.script-editor').scrollIntoView({behavior: 'smooth', block: 'start'});
  detailContent.querySelectorAll('[name="voiceProfile"]').forEach(select =>
    select.dispatchEvent(new Event('change', {bubbles: true})));
}

function renderBattleNarrativePlan(plan, taskId) {
  const beats = plan?.beats || [];
  const cards = beats.map(beat => `<article class="narrative-beat ${beat.structuralPlaceholder ? 'placeholder' : ''}">
    <header><b>${escapeHtml(beat.label)}</b><span>节奏 ×${beat.paceMultiplier.toFixed(2)} · 音乐 ${Math.round(beat.musicIntensity * 100)}%</span></header>
    <p>${escapeHtml(beat.objective)}</p>
    <small>${beat.structuralPlaceholder ? '结构补位：没有匹配的已确认事件，不生成事实断言' : `${escapeHtml(beat.eventType)} · 镜头 ${beat.clipIndex ?? '未匹配'} · ${escapeHtml(beat.confirmedFact)}`}</small>
    <em>${escapeHtml(beat.narrationDirective)}</em>
  </article>`).join('');
  return `<section class="battle-narrative-plan">
    <header><div><small>BATTLE NARRATIVE DIRECTOR</small><h3>五幕战局叙事结构</h3><p>依据人工确认事件规划镜头、节奏、解说与音乐强度。</p></div><div><button type="button" data-storyboard-action="narrative-generate" data-task-id="${taskId}">${beats.length ? '重新规划' : '生成五幕结构'}</button><button type="button" data-storyboard-action="narrative-apply" data-task-id="${taskId}" ${beats.length && !plan?.applied ? '' : 'disabled'}>${plan?.applied ? '已应用' : '应用到剪辑'}</button></div></header>
    <div class="narrative-beat-list">${cards || '<p class="empty">确认事件后生成计划；生成计划不会立即修改当前剪辑。</p>'}</div>
  </section>`;
}

function renderDirectorReviewBoard(reviews, taskId, aiRuntime) {
  const review = reviews?.[0];
  const messages = review?.messages || [];
  const opinions = messages.map(message => `<article><header><b>${escapeHtml(message.roleName || '总导演主持人')}</b><span>第 ${message.roundNo} 轮 · ${(message.elapsedMs / 1000).toFixed(1)}s</span></header><p>${escapeHtml(message.content?.summary || '已提交结构化意见')}</p><small>事件 ${(message.citedEventIds || []).length} · 镜头 ${(message.citedClipIndexes || []).length} · Token ${message.inputTokens + message.outputTokens}</small></article>`).join('');
  const waiting = review?.status === 'AWAITING_USER';
  const actionable = ['ACCEPTED','MODIFIED'].includes(review?.status);
  const currentModel = aiRuntime?.activeTextModel || '读取中';
  const engineLabel = aiRuntime?.local ? `本地 Ollama · ${currentModel}` : `云端 ${aiRuntime?.provider || ''} · ${currentModel}`;
  const historicalCloudFailure = aiRuntime?.local && review?.status === 'FAILED'
    && /API Key/i.test(review?.moderatorSummary || '');
  return `<section class="director-review-board">
    <header><div><small>AI DIRECTOR REVIEW BOARD</small><h3>AI 导演评审会</h3><p>事实、剧情、节奏和受众四个角色独立评审，由总导演检查分歧与共识；结论不会自动修改时间线。</p><p class="director-runtime"><b>当前评审引擎</b><span>${escapeHtml(engineLabel)}</span>${review ? `<small>当前记录使用：${escapeHtml(review.modelVersion || '未知模型')}</small>` : ''}</p>${historicalCloudFailure ? '<p class="director-runtime-tip">这是一条切换到本地模式之前留下的云端失败记录；点击“重新评审”将使用本地模型，不需要云端 API Key。</p>' : ''}</div><div><select data-review-rounds><option value="1">1 轮（推荐）</option><option value="2">2 轮</option></select><button type="button" data-storyboard-action="director-review-start" data-task-id="${taskId}">${review ? '重新评审' : '召开评审会'}</button></div></header>
    ${review ? `<div class="director-metrics"><span>状态 ${escapeHtml(review.status)}</span><span>共识度 ${Math.round(review.consensusScore * 100)}%</span><span>${review.inputTokens + review.outputTokens} Token</span><span>${(review.elapsedMs / 1000).toFixed(1)} 秒</span><span>${escapeHtml(review.modelVersion || '')}</span></div><p>${escapeHtml(review.moderatorSummary || '')}</p><div class="director-review-messages">${opinions}</div><div class="diagnostics-actions">${waiting ? `<button type="button" data-storyboard-action="director-review-accept" data-task-id="${taskId}" data-review-id="${review.id}">接受</button><button type="button" data-storyboard-action="director-review-modify" data-task-id="${taskId}" data-review-id="${review.id}">修改后接受</button><button type="button" data-storyboard-action="director-review-reject" data-task-id="${taskId}" data-review-id="${review.id}">拒绝</button>` : ''}${actionable ? `<button type="button" data-storyboard-action="director-review-apply" data-task-id="${taskId}" data-review-id="${review.id}">应用到时间线</button>` : ''}</div>` : '<p class="empty">建议先确认游戏事件，再进行 1 轮评审；本地模型较慢时不要选择 2 轮。</p>'}
  </section>`;
}

function renderDirectorIntelligence(profile, quality, packs) {
  const issues = (quality?.issues || []).map(item => `<li class="${item.severity.toLowerCase()}"><b>${escapeHtml(item.type)}</b><span>${escapeHtml(item.message)}</span><small>${item.clipIndex ? `镜头 ${item.clipIndex} · ` : ''}${escapeHtml(item.evidence || '')}</small></li>`).join('');
  return `<section class="director-intelligence">
    <header><div><small>PERSONAL DIRECTOR PROFILE</small><h3>个人导演档案与一致性检查</h3><p>${escapeHtml(profile.summary)}</p></div><b>${quality.score} / 100</b></header>
    <div class="director-metrics"><span>修改样本 ${profile.decisionCount}</span><span>镜头长度 ${Math.round(profile.preferredDurationRatio * 100)}%</span><span>文案密度 ${Math.round(profile.preferredTextDensityRatio * 100)}%</span><span>偏好特效 ${escapeHtml(profile.preferredEffects.join('、') || '尚未形成')}</span></div>
    <details ${quality.passed ? '' : 'open'}><summary>${escapeHtml(quality.summary)}</summary><ul>${issues || '<li>没有发现连续性或事实冲突。</li>'}</ul></details>
    <div class="knowledge-pack-manager"><strong>游戏知识包</strong>${packs.map(pack => `<a href="/api/knowledge-packs/${encodeURIComponent(pack.code)}/export">导出 ${escapeHtml(pack.name)}</a>`).join('')}<label>导入知识包 JSON<input type="file" accept="application/json,.json" data-knowledge-pack-import></label></div>
  </section>`;
}

function renderCommunityEcosystem(taskId, packs, resources, variants, report) {
  const resourceCards = (resources || []).map(item => `<article><header><b>${escapeHtml(item.name)}</b><span>${escapeHtml(item.resourceType === 'KNOWLEDGE_PACK' ? '知识包' : '剪辑风格')}</span></header><p>${escapeHtml(item.description)}</p><small>${escapeHtml(item.authorName)} · ${escapeHtml(item.licenseCode)} · 安装 ${item.installCount}</small><footer><button type="button" data-community-install="${item.id}">安装</button><a href="/api/community/resources/${item.id}/export">导出</a></footer></article>`).join('');
  const variantCards = (variants || []).map(item => `<article><header><b>${escapeHtml(item.name)}</b><span>${escapeHtml(item.status)}</span></header><p>${escapeHtml(item.strategy.narrativeFocus)}</p><small>目标 ${item.strategy.targetDurationSeconds} 秒 · 节奏 ×${Number(item.strategy.paceMultiplier).toFixed(2)} · 复用源录像</small><footer>${item.generatedTaskId ? `<button type="button" data-open-task="${item.generatedTaskId}">查看成片任务</button>` : `<button type="button" data-community-materialize="${item.id}">创建独立成片任务</button>`}</footer></article>`).join('');
  return `<section class="community-ecosystem" data-community-task="${taskId}">
    <header><div><small>COMMUNITY ECOSYSTEM</small><h3>创作者社区与多版本工作台</h3><p>分享知识包和剪辑规则；同一段录像规划剧情版、攻略版、搞笑版和复盘版。</p></div><div><button type="button" data-storyboard-action="variants-generate" data-task-id="${taskId}">${variants?.length ? '重新生成四版策略' : '生成四种版本'}</button><button type="button" data-storyboard-action="decision-report" data-task-id="${taskId}">生成决策报告</button><a href="/api/tasks/${taskId}/decision-report/export">导出 Markdown</a></div></header>
    <div class="community-publish"><strong>发布到本地社区</strong>${packs.map(pack => `<button type="button" data-community-publish-pack="${escapeHtml(pack.code)}">分享 ${escapeHtml(pack.name)}</button>`).join('')}<button type="button" data-community-publish-style="HUMOROUS">分享搞笑剪辑规则</button></div>
    <div class="creative-variant-grid">${variantCards || '<p class="empty">尚未生成多版本方案。</p>'}</div>
    <details><summary>社区资源 ${resources?.length || 0} 项</summary><div class="community-resource-grid">${resourceCards || '<p class="empty">尚未分享资源，可先发布当前知识包或剪辑风格。</p>'}</div></details>
    <p class="decision-report-status">${report ? `最近报告：${new Date(report.generatedAt).toLocaleString()} · 版本 ${report.reportVersion}` : '尚未生成剪辑决策报告。'}</p>
  </section>`;
}

function renderGameEventTimeline(events, knowledgePack, narrativePlan, taskId) {
  const statusText = {AI_SUGGESTED:'AI 建议，等待确认', CONFIRMED:'已确认，可用于文案', NEEDS_REVIEW:'需要进一步核对'};
  const cards = events.map(item => `
    <article class="game-event-card ${item.confirmationStatus.toLowerCase()}" data-game-event="${item.id}">
      <header><div><strong>${formatDuration(item.startSeconds)}–${formatDuration(item.endSeconds)}</strong><small>${escapeHtml(statusText[item.confirmationStatus] || item.confirmationStatus)}</small></div><b>置信度 ${Math.round(item.confidence * 100)}%</b></header>
      <div class="game-event-form">
        <label>事件类型<input name="eventType" maxlength="40" value="${escapeHtml(item.eventType)}"></label>
        <label>重要程度<input name="importance" type="number" min="0" max="100" value="${item.importance}"></label>
        <label class="game-event-description">事实描述<textarea name="eventDescription" maxlength="500">${escapeHtml(item.description)}</textarea></label>
        <label>确认状态<select name="confirmationStatus"><option value="AI_SUGGESTED" ${item.confirmationStatus === 'AI_SUGGESTED' ? 'selected' : ''}>尚未确认</option><option value="CONFIRMED" ${item.confirmationStatus === 'CONFIRMED' ? 'selected' : ''}>确认事实</option><option value="NEEDS_REVIEW" ${item.confirmationStatus === 'NEEDS_REVIEW' ? 'selected' : ''}>需要复核</option></select></label>
      </div>
      <details><summary>为什么识别为这个事件 · ${item.evidence.length} 条证据</summary><ul>${item.evidence.map(evidence => `<li><b>${escapeHtml(evidence.sourceType)}</b><span>${escapeHtml(evidence.content)}</span><small>${formatDuration(evidence.timestampSeconds)}${evidence.frameIndex == null ? '' : ` · 帧 ${evidence.frameIndex}`}</small></li>`).join('')}</ul></details>
      <button type="button" data-storyboard-action="event-save" data-task-id="${taskId}" data-event-id="${item.id}">保存事件判断</button>
    </article>`).join('');
  return `<section class="game-event-timeline">
    <header><div><small>EXPLAINABLE GAME EVENTS</small><h3>可解释游戏事件时间线</h3><p>${escapeHtml(knowledgePack.name)}：${escapeHtml(knowledgePack.description)}</p></div><button type="button" data-storyboard-action="events-regenerate-script" data-task-id="${taskId}">用已确认事件重新生成文案</button></header>
    <p class="game-event-guardrail">只有标记为“确认事实”的事件会进入文案提示词；OCR、转写和 AI 推断只作为待核对证据。</p>
    <div class="game-event-list">${cards || '<p class="empty">事件时间线尚未生成，请重新启动任务完成视觉分析与高光筛选。</p>'}</div>
    ${renderBattleNarrativePlan(narrativePlan, taskId)}
  </section>`;
}

async function saveGameEventCard(taskId, card) {
  return requestJson(`/api/tasks/${taskId}/events/${card.dataset.gameEvent}`, {
    method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
      eventType:card.querySelector('[name="eventType"]').value,
      description:card.querySelector('[name="eventDescription"]').value,
      importance:Number(card.querySelector('[name="importance"]').value),
      confirmationStatus:card.querySelector('[name="confirmationStatus"]').value
    })
  });
}

function captureStoryboardViewState(taskId) {
  const currentTaskId = storyboardWorkspace?.querySelector('[data-task-id]')?.dataset.taskId;
  if (currentTaskId !== taskId) return null;
  const active = document.activeElement;
  const activeCard = active?.closest?.('[data-storyboard-segment]');
  const source = storyboardWorkspace.querySelector('[data-editor-preview]');
  return {
    dialogScrollTop: storyboardDialog.scrollTop,
    pageScrollY: window.scrollY,
    sourceTime: source?.currentTime || 0,
    sourceWasPlaying: Boolean(source && !source.paused),
    activeName: active?.name || null,
    activeClip: activeCard?.dataset.storyboardSegment || null,
    selectionStart: typeof active?.selectionStart === 'number' ? active.selectionStart : null,
    openDetails: [...storyboardWorkspace.querySelectorAll('details[open]')]
      .map(item => item.className).filter(Boolean)
  };
}

function restoreStoryboardViewState(state) {
  if (!state) return;
  storyboardDialog.scrollTop = state.dialogScrollTop;
  window.scrollTo({top:state.pageScrollY, behavior:'instant'});
  const source = storyboardWorkspace.querySelector('[data-editor-preview]');
  if (source) {
    source.currentTime = state.sourceTime;
    if (state.sourceWasPlaying) source.play().catch(() => {});
  }
  state.openDetails.forEach(className => {
    const selector = `details.${className.trim().split(/\s+/).join('.')}`;
    storyboardWorkspace.querySelector(selector)?.setAttribute('open', '');
  });
  if (!state.activeName) return;
  const scope = state.activeClip
    ? storyboardWorkspace.querySelector(`[data-storyboard-segment="${state.activeClip}"]`)
    : storyboardWorkspace;
  const target = [...(scope?.querySelectorAll(`[name="${state.activeName}"]`) || [])][0];
  target?.focus({preventScroll:true});
  if (target && state.selectionStart !== null && typeof target.setSelectionRange === 'function') {
    target.setSelectionRange(state.selectionStart, state.selectionStart);
  }
}

async function loadStoryboardEditor(taskId) {
  const viewState = captureStoryboardViewState(taskId);
  clearInterval(storyboardProgressTimer);
  if (!storyboardDialog.open) storyboardDialog.showModal();
  if (!viewState) storyboardWorkspace.innerHTML = '<p class="empty">正在读取完整分镜时间线…</p>';
  const [storyboard, localAssets, placements, editorTimeline, waveform, revisions, gameEvents, knowledgePack, narrativePlan, directorReviews, directorProfile, narrativeQuality, knowledgePacks, communityResources, creativeVariants, decisionReport, aiRuntime] = await Promise.all([
    requestJson(`/api/tasks/${taskId}/storyboard`),
    requestJson('/api/assets?importStatus=DOWNLOADED&limit=100'),
    requestJson(`/api/tasks/${taskId}/storyboard/assets`),
    requestJson(`/api/tasks/${taskId}/editor`),
    requestJson(`/api/tasks/${taskId}/editor/waveform?points=320`),
    requestJson(`/api/tasks/${taskId}/editor/revisions`),
    requestJson(`/api/tasks/${taskId}/events`),
    requestJson(`/api/tasks/${taskId}/events/knowledge-pack`),
    requestJson(`/api/tasks/${taskId}/events/narrative-plan`),
    requestJson(`/api/tasks/${taskId}/director-reviews`),
    requestJson('/api/director-profile'),
    requestJson(`/api/tasks/${taskId}/quality/narrative-consistency`),
    requestJson('/api/knowledge-packs'),
    requestJson('/api/community/resources'),
    requestJson(`/api/tasks/${taskId}/variants`),
    requestJson(`/api/tasks/${taskId}/decision-report`),
    requestJson('/api/ai-settings/runtime')
  ]);
  const totalDuration = storyboard.segments.reduce((sum, item) => sum + item.endSeconds - item.startSeconds, 0);
  storyboardWorkspace.innerHTML = `
    <section class="detail-block storyboard-editor" data-task-id="${taskId}" data-review-enabled="${storyboard.reviewEnabled}" data-approved="${storyboard.approved}">
      ${renderGameEventTimeline(gameEvents, knowledgePack, narrativePlan, taskId)}
      ${renderDirectorReviewBoard(directorReviews, taskId, aiRuntime)}
      ${renderDirectorIntelligence(directorProfile, narrativeQuality, knowledgePacks)}
      ${renderCommunityEcosystem(taskId, knowledgePacks, communityResources, creativeVariants, decisionReport)}
      <header class="storyboard-editor-head"><div><small>EDITOR WORKSPACE</small><h3>${escapeHtml(storyboard.title || '自由剪辑与分镜')}</h3><p>${escapeHtml(storyboard.synopsis || '')}</p></div>
      <div class="storyboard-head-actions"><button type="button" data-storyboard-action="auto-assets" data-task-id="${taskId}">自动匹配并下载素材</button>${storyboard.approved ? '<span class="storyboard-approved">已确认 / 自动模式</span>' : '<span class="storyboard-review-pending">修改后请使用底部主按钮保存并继续</span>'}</div></header>
      <div class="storyboard-stats"><span>${storyboard.segments.length} 个分镜</span><span>预计素材时长 ${formatDuration(totalDuration)}</span><span>支持拖拽排序与入点/出点修剪</span></div>
      <section class="editor-source-monitor"><video controls preload="metadata" src="/api/tasks/${taskId}/source" data-editor-preview></video><div><strong>源视频监视器</strong><small>在时间线上选择位置会同步跳转原片；可直接播放确认剪切点。</small></div></section>
      <section class="storyboard-visual-timeline" data-visual-timeline>
        <header><div><strong>自由剪辑时间线</strong><small>单击选择片段或定位播放头；剪断后可与右侧连续片段重新连接</small></div><div class="timeline-toolbar"><button type="button" data-editor-history="UNDO">撤回上一步</button><button type="button" data-editor-history="REDO">恢复撤回</button><button type="button" data-editor-action="SPLIT" disabled>刀片分割</button><button type="button" data-editor-action="MERGE" disabled>连接右侧片段</button><button type="button" data-editor-action="DELETE" disabled>删除片段</button><label>缩放 <input type="range" min="24" max="120" value="54" data-timeline-zoom></label><b data-history-count></b></div></header>
        <div class="timeline-selection-status" data-timeline-selection>请选择片段；点击片段内部可设置分割位置</div>
        <section class="timeline-track-manager" data-track-manager>
          <form data-track-add-form><select name="trackType"><option value="OVERLAY">叠加轨道</option><option value="AUDIO">音频轨道</option><option value="SUBTITLE">字幕轨道</option></select><input name="trackName" maxlength="60" placeholder="新轨道名称" required><button type="submit">新增轨道</button></form>
          <div data-track-list></div>
        </section>
        <div class="storyboard-waveform" data-storyboard-waveform></div>
        <div class="storyboard-track-scroll"><div class="storyboard-track" data-storyboard-track></div></div>
      </section>
      <details class="revision-tree-panel" open><summary>工程版本树 · ${revisions.length} 个版本</summary><div class="revision-tree-list">${renderRevisionTree(revisions)}</div></details>
      <section class="storyboard-pipeline-progress" data-storyboard-progress><p>正在读取处理进度…</p></section>
      <p class="bilibili-asset-login-hint">自动接取 Bilibili 视频和专栏素材前必须先完成上方 Bilibili 登录；未登录时只会使用本地素材与开放许可素材源。</p>
      <p class="effect-note">修改镜头起止时间会直接改变最终成片使用的源视频范围；保存文案后，后续配音、字幕和渲染会使用最新内容。</p>
      <div class="storyboard-grid storyboard-linear">${storyboard.segments.map(segment => `
        <article class="storyboard-card" data-storyboard-segment="${segment.clipIndex}">
          <img src="/api/tasks/${taskId}/storyboard/segments/${segment.clipIndex}/thumbnail" alt="分镜 ${segment.clipIndex} 缩略图" loading="lazy">
          <header><strong>分镜 ${segment.clipIndex}</strong><span>${escapeHtml(segment.eventType || '其他')} · AI ${segment.finalScore} 分</span></header>
          <div class="storyboard-order"><button type="button" data-storyboard-action="move" data-direction="UP" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}" ${segment.clipIndex === 1 ? 'disabled' : ''}>上移</button><button type="button" data-storyboard-action="move" data-direction="DOWN" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}" ${segment.clipIndex === storyboard.segments.length ? 'disabled' : ''}>下移</button></div>
          <p class="storyboard-description">${escapeHtml(segment.description || '')}</p>
          <div class="storyboard-controls"><label><input name="locked" type="checkbox" ${segment.locked ? 'checked' : ''}>锁定：AI 重写时保持此镜</label><label><input name="excluded" type="checkbox" ${segment.excluded ? 'checked' : ''}>排除：最终时间线跳过此镜</label></div>
          <div class="storyboard-time"><label>开始秒数<input name="startSeconds" type="number" min="0" step="0.1" value="${segment.startSeconds.toFixed(2)}"></label><label>结束秒数<input name="endSeconds" type="number" min="0.01" step="0.1" value="${segment.endSeconds.toFixed(2)}"></label></div>
          <label>解说文案<textarea name="narration" maxlength="500">${escapeHtml(segment.narration)}</textarea></label>
          <label>字幕<input name="subtitle" maxlength="500" value="${escapeHtml(segment.subtitle)}"></label>
          <label>特效提示<input name="effectCue" maxlength="200" value="${escapeHtml(segment.effectCue)}"></label>
          <label>AI 重写要求<input name="rewriteInstruction" maxlength="500" placeholder="例如：更紧凑、更有悬念，保持事实不变"></label>
          <section class="storyboard-assets">
            <strong>添加素材内容</strong>
            <div class="storyboard-asset-picker">
              <select name="assetId"><option value="">选择本地素材库…</option>${localAssets.map(asset => `<option value="${asset.id}">${escapeHtml(asset.title)} · ${escapeHtml(asset.assetType)}</option>`).join('')}</select>
              <input name="assetInstruction" maxlength="500" placeholder="例如：放到第 3 镜右下角；作为背景；爆炸时播放">
              <button type="button" data-storyboard-action="asset-ai" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">AI 分析并添加</button>
              <button type="button" data-storyboard-action="asset-manual" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">按指令添加</button>
            </div>
            <div class="storyboard-placement-list">${placements.filter(item => item.clipIndex === segment.clipIndex).map(item => `<div class="storyboard-placement-item" data-placement-item="${item.id}"><strong>${escapeHtml(item.title)}</strong><small>${item.assetType === 'VIDEO' ? '自动剪切' : item.assetType === 'MEME' ? 'PNG/WebP 透明贴图' : item.assetType === 'BGM' ? '背景混音' : '事件混音'}</small><select name="placementPosition">${['TOP_LEFT','TOP_RIGHT','CENTER','BOTTOM_LEFT','BOTTOM_RIGHT','FULL_SCREEN','AUDIO_TRACK'].map(position => `<option value="${position}" ${item.position === position ? 'selected' : ''}>${position}</option>`).join('')}</select><label>开始秒<input name="placementStart" type="number" min="0" step="0.05" value="${item.startOffsetSeconds ?? 0}"></label><label>结束秒（留空到镜头末）<input name="placementEnd" type="number" min="0" step="0.05" value="${item.endOffsetSeconds ?? ''}"></label><label>缩放 %<input name="placementScale" type="number" min="5" max="200" value="${item.scalePercent ?? 38}"></label><label>动画<select name="placementAnimation">${['NONE','FADE','POP','SLIDE','BOUNCE'].map(animation => `<option value="${animation}" ${item.animation === animation ? 'selected' : ''}>${animation}</option>`).join('')}</select></label><label>层级<input name="placementZIndex" type="number" min="-100" max="100" value="${item.zIndex ?? 0}"></label><label><input name="placementCutout" type="checkbox" ${item.cutoutApplied ? 'checked' : ''}>抠图/透明叠加</label><input name="placementInstruction" value="${escapeHtml(item.instruction || '')}" placeholder="素材处理说明"><button type="button" data-storyboard-action="asset-save" data-task-id="${taskId}" data-placement-id="${item.id}">保存素材设置</button><button type="button" data-storyboard-action="asset-remove" data-task-id="${taskId}" data-placement-id="${item.id}">移除</button></div>`).join('') || '<small>尚未添加额外素材</small>'}</div>
          </section>
          <div class="storyboard-actions"><button type="button" data-storyboard-action="save" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">保存此分镜</button><button type="button" data-storyboard-action="rewrite" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">AI 重写此镜</button></div>
        </article>`).join('')}</div>
      <footer class="storyboard-continue-bar"><div><strong>修改完成了吗？</strong><small>点击后会先保存全部分镜，再明确启动配音、时间线规划和视频渲染。</small></div><button type="button" data-storyboard-action="save-all-continue" data-task-id="${taskId}">保存全部修改并执行下一步 →</button></footer>
    </section>`;
  mountStoryboardTimeline(taskId, editorTimeline, waveform);
  storyboardWorkspace.querySelectorAll('[data-placement-item]').forEach(itemNode => {
    const placement = placements.find(item => item.id === itemNode.dataset.placementItem);
    if (!placement || !['SFX', 'BGM'].includes(placement.assetType)) return;
    const controls = document.createElement('div');
    controls.className = 'storyboard-sfx-controls';
    controls.innerHTML = `<label>音量 %<input name="placementVolume" type="number" min="0" max="200" value="${placement.volumePercent ?? 48}"></label><label>淡入秒<input name="placementFadeIn" type="number" min="0" step="0.05" value="${placement.fadeInSeconds ?? 0}"></label><label>淡出秒<input name="placementFadeOut" type="number" min="0" step="0.05" value="${placement.fadeOutSeconds ?? 0}"></label><audio controls preload="none" src="${placement.previewUrl}"></audio><small>授权：${escapeHtml(placement.licenseCode || '未记录')} · ${escapeHtml(placement.attribution || '无署名要求')}</small>`;
    itemNode.querySelector('[name="placementCutout"]')?.closest('label')?.before(controls);
  });
  restoreStoryboardViewState(viewState);
  storyboardWorkspace.querySelector('[data-knowledge-pack-import]')?.addEventListener('change', async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const body = JSON.parse(await file.text());
      await requestJson('/api/knowledge-packs', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
      await loadStoryboardEditor(taskId);
    } catch (error) { window.alert(`知识包导入失败：${error.message}`); }
  });
  storyboardWorkspace.querySelectorAll('[data-community-install]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try { await requestJson(`/api/community/resources/${button.dataset.communityInstall}/install`, {method:'POST'}); await loadStoryboardEditor(taskId); }
    catch (error) { window.alert(error.message); button.disabled = false; }
  }));
  storyboardWorkspace.querySelectorAll('[data-community-materialize]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try { await requestJson(`/api/tasks/${taskId}/variants/${button.dataset.communityMaterialize}/materialize`, {method:'POST'}); await loadStoryboardEditor(taskId); }
    catch (error) { window.alert(error.message); button.disabled = false; }
  }));
  storyboardWorkspace.querySelectorAll('[data-open-task]').forEach(button => button.addEventListener('click', () => {
    clearInterval(storyboardProgressTimer); storyboardDialog.close(); openTaskDetails(button.dataset.openTask);
  }));
  storyboardWorkspace.querySelectorAll('[data-community-publish-pack]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try { await requestJson(`/api/community/knowledge-packs/${encodeURIComponent(button.dataset.communityPublishPack)}`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({authorName:'本地创作者',licenseCode:'CC-BY-4.0',tags:['游戏知识','事件识别']})}); await loadStoryboardEditor(taskId); }
    catch (error) { window.alert(error.message); button.disabled = false; }
  }));
  storyboardWorkspace.querySelectorAll('[data-community-publish-style]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try { await requestJson(`/api/community/styles/${encodeURIComponent(button.dataset.communityPublishStyle)}`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({authorName:'本地创作者',licenseCode:'CC-BY-4.0',tags:['剪辑规则','风格配置']})}); await loadStoryboardEditor(taskId); }
    catch (error) { window.alert(error.message); button.disabled = false; }
  }));
  mountRevisionTree(taskId);
  storyboardWorkspace.scrollTo({top:0, behavior:'smooth'});
  await updateStoryboardProgress(taskId);
  if (!window.gameNarratorTasks?.isStreamConnected()) {
    storyboardProgressTimer = setInterval(() => updateStoryboardProgress(taskId), 2000);
  }
}

function renderRevisionTree(revisions) {
  const byId = new Map(revisions.map(item => [item.id, item]));
  const depth = item => { let value = 0, parent = item.parentRevisionId; while (parent && byId.has(parent) && value < 20) { value++; parent = byId.get(parent).parentRevisionId; } return value; };
  return revisions.map(item => `<article class="revision-tree-item ${item.current ? 'current' : ''}" style="--revision-depth:${depth(item)}">
    <span><b>${escapeHtml(item.label || `版本 ${item.revisionNo}`)}</b><small>${escapeHtml(item.changeSummary || item.changeType)} · ${new Date(item.createdAt).toLocaleString()}</small></span>
    <i>${item.childCount ? `${item.childCount} 个分支` : '叶节点'}</i>
    <button type="button" data-revision-name="${item.id}">命名</button>
    <button type="button" data-revision-checkout="${item.id}" ${item.current ? 'disabled' : ''}>${item.current ? '当前版本' : '切换到此版本'}</button>
  </article>`).join('');
}

function mountRevisionTree(taskId) {
  const panel = storyboardWorkspace.querySelector('.revision-tree-panel');
  panel?.addEventListener('click', async event => {
    const nameButton = event.target.closest('[data-revision-name]');
    const checkoutButton = event.target.closest('[data-revision-checkout]');
    if (!nameButton && !checkoutButton) return;
    const button = nameButton || checkoutButton; button.disabled = true;
    try {
      if (nameButton) {
        const label = window.prompt('输入版本名称（最多 100 字）');
        if (!label?.trim()) return;
        await requestJson(`/api/tasks/${taskId}/editor/revisions/${nameButton.dataset.revisionName}`, {
          method:'PATCH', headers:{'Content-Type':'application/json'}, body:JSON.stringify({label:label.trim()})
        });
      } else {
        if (!window.confirm('切换版本会使当前旧配音和渲染结果失效，确定继续吗？')) return;
        await requestJson(`/api/tasks/${taskId}/editor/revisions/${checkoutButton.dataset.revisionCheckout}/checkout`, {method:'POST'});
      }
      await loadStoryboardEditor(taskId);
    } catch (error) { window.alert(error.message); button.disabled = false; }
  });
}

function mountStoryboardTimeline(taskId, timeline, waveform) {
  const panel = storyboardWorkspace.querySelector('[data-visual-timeline]');
  if (!panel) return;
  panel._timeline = timeline;
  panel._waveform = waveform;
  mountTrackManager(panel, taskId);
  const zoom = panel.querySelector('[data-timeline-zoom]');
  const draw = () => drawStoryboardTimeline(panel, taskId, Number(zoom.value));
  zoom.addEventListener('input', draw);
  panel.querySelectorAll('[data-editor-history]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      await editorTimelineCommand(taskId, button.dataset.editorHistory, {});
      await loadStoryboardEditor(taskId);
    } catch (error) { button.title = error.message; button.disabled = false; }
  }));
  panel.querySelectorAll('[data-editor-action]').forEach(button => button.addEventListener('click', async () => {
    const clip = panel._timeline.clips.find(item => item.id === panel._selectedClipId);
    if (!clip) return;
    button.disabled = true;
    try {
      if (button.dataset.editorAction === 'SPLIT') {
        const atSeconds = panel._playheadSeconds ?? clip.timelineStartSeconds + clip.durationSeconds / 2;
        await editorTimelineCommand(taskId, 'SPLIT', {clipId:clip.id, atSeconds});
      } else if (button.dataset.editorAction === 'MERGE') {
        await editorTimelineCommand(taskId, 'MERGE', {clipId:clip.id});
      } else {
        await editorTimelineCommand(taskId, 'DELETE', {clipId:clip.id});
      }
      await loadStoryboardEditor(taskId);
    } catch (error) {
      panel.querySelector('[data-timeline-selection]').textContent = error.message;
      button.disabled = false;
    }
  }));
  draw();
}

function mountTrackManager(panel, taskId) {
  const manager = panel.querySelector('[data-track-manager]');
  if (!manager) return;
  const tracks = [...(panel._timeline.tracks || [])].sort((left, right) => left.order - right.order);
  manager.querySelector('[data-track-list]').innerHTML = tracks.map((track, index) => `<article data-editor-track="${escapeHtml(track.id)}">
    <span><b>${escapeHtml(track.name)}</b><small>${escapeHtml(track.type)}</small></span>
    <label><input type="checkbox" data-track-muted ${track.muted ? 'checked' : ''}>静音</label>
    <label><input type="checkbox" data-track-solo ${track.solo ? 'checked' : ''}>独奏</label>
    <button type="button" data-track-move="UP" ${index === 0 ? 'disabled' : ''}>上移</button>
    <button type="button" data-track-move="DOWN" ${index === tracks.length - 1 ? 'disabled' : ''}>下移</button>
    <button type="button" data-track-delete ${track.id === 'video-1' ? 'disabled title="主视频轨道不能删除"' : ''}>删除</button>
  </article>`).join('');
  manager.querySelector('[data-track-add-form]').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button');
    form.elements.trackName.setCustomValidity('');
    button.disabled = true;
    try {
      await editorTimelineCommand(taskId, 'TRACK_ADD', {
        trackType:form.elements.trackType.value, name:form.elements.trackName.value.trim()
      });
      await loadStoryboardEditor(taskId);
    } catch (error) {
      form.elements.trackName.setCustomValidity(error.message);
      form.reportValidity();
      button.disabled = false;
    }
  });
  const list = manager.querySelector('[data-track-list]');
  list.addEventListener('change', async event => {
    if (!event.target.matches('[data-track-muted],[data-track-solo]')) return;
    const row = event.target.closest('[data-editor-track]');
    row.querySelectorAll('input,button').forEach(control => { control.disabled = true; });
    try {
      await editorTimelineCommand(taskId, 'TRACK_STATE', {trackId:row.dataset.editorTrack,
        muted:row.querySelector('[data-track-muted]').checked,
        solo:row.querySelector('[data-track-solo]').checked});
      await loadStoryboardEditor(taskId);
    } catch (error) {
      row.title = error.message;
      row.querySelectorAll('input,button').forEach(control => { control.disabled = false; });
    }
  });
  list.addEventListener('click', async event => {
    const move = event.target.closest('[data-track-move]');
    const remove = event.target.closest('[data-track-delete]');
    if ((!move && !remove) || event.target.disabled) return;
    const row = event.target.closest('[data-editor-track]');
    if (remove && !window.confirm(`删除空轨道“${row.querySelector('b').textContent}”？`)) return;
    row.querySelectorAll('input,button').forEach(control => { control.disabled = true; });
    try {
      await editorTimelineCommand(taskId, remove ? 'TRACK_DELETE' : 'TRACK_MOVE', remove
        ? {trackId:row.dataset.editorTrack}
        : {trackId:row.dataset.editorTrack, direction:move.dataset.trackMove});
      await loadStoryboardEditor(taskId);
    } catch (error) {
      row.title = error.message;
      row.querySelectorAll('input,button').forEach(control => { control.disabled = false; });
    }
  });
}

function drawStoryboardTimeline(panel, taskId, pixelsPerSecond) {
  const timeline = panel._timeline;
  const track = panel.querySelector('[data-storyboard-track]');
  const duration = Math.max(1, timeline.durationSeconds || 1);
  track.style.width = `${Math.max(720, duration * pixelsPerSecond)}px`;
  track.innerHTML = `${panel._playheadSeconds == null ? '' : `<i class="timeline-playhead" style="left:${panel._playheadSeconds * pixelsPerSecond}px"></i>`}${timeline.clips.map((clip, index) => `<article class="storyboard-track-clip ${clip.id === panel._selectedClipId ? 'selected' : ''}" draggable="true" data-editor-clip="${clip.id}" style="left:${clip.timelineStartSeconds * pixelsPerSecond}px;width:${Math.max(42, clip.durationSeconds * pixelsPerSecond)}px">
    <button type="button" class="trim-handle trim-in" data-trim-edge="IN" aria-label="调整片段 ${index + 1} 入点"></button>
    <img src="/api/tasks/${taskId}/storyboard/segments/${index + 1}/thumbnail" alt="片段 ${index + 1}"><span><b>${index + 1}</b><small>${clip.sourceStartSeconds.toFixed(1)}–${clip.sourceEndSeconds.toFixed(1)}s</small></span>
    <button type="button" class="trim-handle trim-out" data-trim-edge="OUT" aria-label="调整片段 ${index + 1} 出点"></button>
  </article>`).join('')}`;
  const history = timeline.history || {};
  panel.querySelector('[data-history-count]').textContent = `${history.revisionCount || 0} 个持久化版本`;
  panel.querySelector('[data-editor-history="UNDO"]').disabled = !history.canUndo;
  panel.querySelector('[data-editor-history="REDO"]').disabled = !history.canRedo;
  const waveform = panel.querySelector('[data-storyboard-waveform]');
  waveform.style.width = track.style.width;
  waveform.innerHTML = panel._waveform?.available ? panel._waveform.points.map(value => `<i style="height:${Math.max(2, value * 42)}px"></i>`).join('') : '<small>当前素材没有可用音频波形</small>';

  let draggedId = null;
  track.querySelectorAll('[data-editor-clip]').forEach(clip => {
    clip.addEventListener('dragstart', event => { draggedId = clip.dataset.editorClip; event.dataTransfer.effectAllowed = 'move'; });
    clip.addEventListener('click', event => {
      if (event.target.closest('[data-trim-edge]')) return;
      const model = timeline.clips.find(item => item.id === clip.dataset.editorClip);
      panel._selectedClipId = model.id;
      panel._playheadSeconds = Math.max(model.timelineStartSeconds + .05,
        Math.min(model.timelineStartSeconds + model.durationSeconds - .05,
          (event.clientX - track.getBoundingClientRect().left) / pixelsPerSecond));
      const preview = storyboardWorkspace.querySelector('[data-editor-preview]');
      if (preview) preview.currentTime = model.sourceStartSeconds + panel._playheadSeconds - model.timelineStartSeconds;
      panel.querySelector('[data-timeline-selection]').textContent = `已选片段 ${timeline.clips.indexOf(model) + 1} · 播放头 ${panel._playheadSeconds.toFixed(2)} 秒`;
      panel.querySelectorAll('[data-editor-action]').forEach(button => button.disabled = false);
      drawStoryboardTimeline(panel, taskId, pixelsPerSecond);
    });
  });
  track.addEventListener('dragover', event => { event.preventDefault(); event.dataTransfer.dropEffect = 'move'; });
  track.addEventListener('drop', async event => {
    event.preventDefault();
    if (!draggedId) return;
    const start = Math.max(0, (event.clientX - track.getBoundingClientRect().left) / pixelsPerSecond);
    track.classList.add('saving');
    try {
      await editorTimelineCommand(taskId, 'MOVE', {clipId:draggedId, trackId:'video-1', timelineStartSeconds:start, snap:true});
      await loadStoryboardEditor(taskId);
    } catch (error) { track.title = error.message; track.classList.remove('saving'); }
  });
  track.querySelectorAll('[data-trim-edge]').forEach(handle => handle.addEventListener('pointerdown', event => {
    event.preventDefault(); event.stopPropagation();
    const clipElement = handle.closest('[data-editor-clip]');
    const clip = timeline.clips.find(item => item.id === clipElement.dataset.editorClip);
    const originX = event.clientX, originStart = clip.sourceStartSeconds, originEnd = clip.sourceEndSeconds;
    handle.setPointerCapture(event.pointerId);
    const move = current => {
      const delta = (current.clientX - originX) / pixelsPerSecond;
      const start = handle.dataset.trimEdge === 'IN' ? Math.min(originEnd - .05, Math.max(0, originStart + delta)) : originStart;
      const end = handle.dataset.trimEdge === 'OUT' ? Math.max(originStart + .05, originEnd + delta) : originEnd;
      clipElement.querySelector('small').textContent = `${start.toFixed(1)}–${end.toFixed(1)}s`;
      clipElement.style.width = `${Math.max(42, (end - start) * pixelsPerSecond)}px`;
    };
    const finish = async current => {
      handle.removeEventListener('pointermove', move); handle.removeEventListener('pointerup', finish);
      const delta = (current.clientX - originX) / pixelsPerSecond;
      const sourceStartSeconds = handle.dataset.trimEdge === 'IN' ? Math.min(originEnd - .05, Math.max(0, originStart + delta)) : originStart;
      const sourceEndSeconds = handle.dataset.trimEdge === 'OUT' ? Math.max(originStart + .05, originEnd + delta) : originEnd;
      try {
        await editorTimelineCommand(taskId, 'TRIM', {clipId:clip.id, sourceStartSeconds, sourceEndSeconds});
        await loadStoryboardEditor(taskId);
      } catch (error) { clipElement.title = error.message; }
    };
    handle.addEventListener('pointermove', move); handle.addEventListener('pointerup', finish);
  }));
}

function editorTimelineCommand(taskId, type, payload) {
  return requestJson(`/api/tasks/${taskId}/editor/commands`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({type, payload})});
}

document.addEventListener('keydown', event => {
  if (!storyboardDialog?.open) return;
  if (event.key === 'Delete' || event.key === 'Backspace') {
    const button = storyboardWorkspace.querySelector('[data-editor-action="DELETE"]:not(:disabled)');
    if (button && !event.target.matches('input,textarea')) { event.preventDefault(); button.click(); }
    return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'b') {
    event.preventDefault(); storyboardWorkspace.querySelector('[data-editor-action="SPLIT"]:not(:disabled)')?.click(); return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'j') {
    event.preventDefault(); storyboardWorkspace.querySelector('[data-editor-action="MERGE"]:not(:disabled)')?.click(); return;
  }
  if (!(event.ctrlKey || event.metaKey)) return;
  const type = event.key.toLowerCase() === 'z' && event.shiftKey ? 'REDO' : event.key.toLowerCase() === 'z' ? 'UNDO' : event.key.toLowerCase() === 'y' ? 'REDO' : null;
  if (!type) return;
  event.preventDefault();
  storyboardWorkspace.querySelector(`[data-editor-history="${type}"]:not(:disabled)`)?.click();
});

document.querySelector('#storyboard-close')?.addEventListener('click', () => { clearInterval(storyboardProgressTimer); storyboardDialog.close(); });
storyboardDialog?.addEventListener('click', event => { if (event.target === storyboardDialog) { clearInterval(storyboardProgressTimer); storyboardDialog.close(); } });
storyboardWorkspace?.addEventListener('click', async event => {
  const action = event.target.closest('[data-storyboard-action]');
  if (action) await handleStoryboardAction(action);
});

async function updateStoryboardProgress(taskId) {
  const panel = storyboardWorkspace?.querySelector('[data-storyboard-progress]');
  if (!panel) return;
  try {
    const task = await requestJson(`/api/tasks/${taskId}`);
    renderStoryboardProgress(task);
  } catch (error) {
    panel.innerHTML = `<p class="task-error">进度读取失败：${escapeHtml(error.message)}</p>`;
  }
}

function renderStoryboardProgress(task) {
    const panel = storyboardWorkspace?.querySelector('[data-storyboard-progress]');
    if (!panel) return;
    const completed = task.stages.filter(stage => stage.status === 'COMPLETED').length;
    const running = task.stages.find(stage => stage.status === 'RUNNING');
    const waiting = task.stages.find(stage => stage.status === 'PENDING');
    const exact = Math.round(((completed + (running?.progress || 0) / 100) / task.stages.length) * 100);
    const currentText = running ? `${stageNames[running.type]} · ${running.progress}%`
      : task.status === 'WAITING_REVIEW' ? '等待保存并确认分镜'
      : task.status === 'COMPLETED' ? '全部处理完成'
      : task.status === 'FAILED' ? `处理失败：${task.failureReason || '请查看诊断日志'}` : '准备进入下一阶段';
    const voiceHint = running?.type === 'VOICE_GENERATION'
      ? '正在逐段生成 AI 语音；文案较长时会明显慢于其他阶段，请不要重复点击。' : '';
    panel.innerHTML = `<header><div><small>当前阶段</small><strong>${escapeHtml(currentText)}</strong></div><b>${exact}%</b></header>
      <div class="storyboard-overall-progress"><i style="width:${exact}%"></i></div>
      <div class="storyboard-stage-strip">${task.stages.map(stage => `<span class="${stage.status.toLowerCase()}"><i></i>${escapeHtml(stageNames[stage.type])}<b>${stage.progress}%</b></span>`).join('')}</div>
      <p>${voiceHint || (waiting ? `下一阶段：${stageNames[waiting.type]}` : '正在整理最终结果')}</p>`;
}

window.addEventListener('gamenarrator:tasks', event => {
  if (detailDialog?.open && activeTaskId) {
    const active = event.detail.find(item => item.id === activeTaskId);
    if (active?.stages.some(stage => stage.type === 'RENDERING' && stage.status === 'RUNNING')) refreshRenderPreview(activeTaskId);
  }
  if (!storyboardDialog?.open) return;
  const taskId = storyboardWorkspace?.querySelector('[data-task-id]')?.dataset.taskId;
  const task = event.detail.find(item => item.id === taskId);
  if (task) renderStoryboardProgress(task);
});

async function handleStoryboardAction(button) {
  const taskId = button.dataset.taskId;
  button.disabled = true;
  try {
    if (button.dataset.storyboardAction === 'director-review-start') {
      const rounds = Number(storyboardWorkspace.querySelector('[data-review-rounds]')?.value || 1);
      button.textContent = 'AI 评审中，请勿关闭窗口…';
      await requestJson(`/api/tasks/${taskId}/director-reviews`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({rounds})});
      await loadStoryboardEditor(taskId); return;
    }
    if (button.dataset.storyboardAction?.startsWith('director-review-')) {
      const action = button.dataset.storyboardAction;
      const reviewId = button.dataset.reviewId;
      if (action === 'director-review-apply') await requestJson(`/api/tasks/${taskId}/director-reviews/${reviewId}/apply`, {method:'POST'});
      else {
        const mapping = {'director-review-accept':'ACCEPTED','director-review-modify':'MODIFIED','director-review-reject':'REJECTED'};
        const modification = action === 'director-review-modify' ? prompt('请写明你对最终决策的修改要求') : null;
        if (action === 'director-review-modify' && !modification) { button.disabled=false; return; }
        await requestJson(`/api/tasks/${taskId}/director-reviews/${reviewId}/decision`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({action:mapping[action], modification})});
      }
      await loadStoryboardEditor(taskId); return;
    }
    if (button.dataset.storyboardAction === 'variants-generate') {
      await requestJson(`/api/tasks/${taskId}/variants/generate`, {method:'POST'});
      await loadStoryboardEditor(taskId);
      return;
    }
    if (button.dataset.storyboardAction === 'decision-report') {
      await requestJson(`/api/tasks/${taskId}/decision-report`, {method:'POST'});
      await loadStoryboardEditor(taskId);
      return;
    }
    if (button.dataset.storyboardAction === 'narrative-generate') {
      const eventCards = [...storyboardWorkspace.querySelectorAll('[data-game-event]')];
      for (const eventCard of eventCards) await saveGameEventCard(taskId, eventCard);
      await requestJson(`/api/tasks/${taskId}/events/narrative-plan/generate`, {method:'POST'});
      await loadStoryboardEditor(taskId);
      return;
    }
    if (button.dataset.storyboardAction === 'narrative-apply') {
      button.textContent = '正在应用五幕结构…';
      await requestJson(`/api/tasks/${taskId}/events/narrative-plan/apply`, {method:'POST'});
      await loadStoryboardEditor(taskId);
      return;
    }
    if (button.dataset.storyboardAction === 'event-save') {
      await saveGameEventCard(taskId, button.closest('[data-game-event]'));
      button.textContent = '事件已保存';
      setTimeout(() => { button.textContent = '保存事件判断'; button.disabled = false; }, 1000);
      return;
    }
    if (button.dataset.storyboardAction === 'events-regenerate-script') {
      const eventCards = [...storyboardWorkspace.querySelectorAll('[data-game-event]')];
      for (const eventCard of eventCards) await saveGameEventCard(taskId, eventCard);
      button.textContent = '正在按已确认事实生成文案…';
      await requestJson(`/api/tasks/${taskId}/events/regenerate-script`, {method:'POST'});
      await loadStoryboardEditor(taskId);
      return;
    }
    if (button.dataset.storyboardAction === 'save-all-continue') {
      const editor = storyboardWorkspace.querySelector('.storyboard-editor');
      await saveAllStoryboardSegments(taskId, button);
      button.textContent = '修改已保存，正在启动下一阶段…';
      if (editor.dataset.reviewEnabled === 'true' && editor.dataset.approved !== 'true') {
        await requestJson(`/api/tasks/${taskId}/storyboard/approve`, {method:'POST'});
        editor.dataset.approved = 'true';
      } else {
        const response = await fetch(`/api/tasks/${taskId}/start`, {method:'POST'});
        if (!response.ok) throw await readApiError(response);
      }
      button.textContent = '已执行：等待下一阶段';
      await updateStoryboardProgress(taskId);
      return;
    }
    if (['auto-assets','asset-remove','asset-save','move','rewrite','asset-ai','asset-manual'].includes(button.dataset.storyboardAction)) {
      await saveAllStoryboardSegments(taskId);
    }
    if (button.dataset.storyboardAction === 'auto-assets') {
      const result = await requestJson(`/api/tasks/${taskId}/storyboard/assets/auto`, {method:'POST'});
      const warning = result.warnings?.length ? `；部分来源不可用：${result.warnings.slice(0,3).join('；')}` : '';
      const bilibili = result.bilibiliLoginRequired ? '；如需使用 Bilibili 素材，请先登录' : '';
      const outcome = result.assignedCount > 0
        ? `已自动挂载 ${result.assignedCount} 项素材`
        : '当前没有找到符合授权及匹配条件的可用素材';
      window.alert(`${outcome}${bilibili}${warning}`);
      await loadStoryboardEditor(taskId); return;
    }
    if (button.dataset.storyboardAction === 'asset-remove') {
      await fetch(`/api/tasks/${taskId}/storyboard/assets/${button.dataset.placementId}`, {method:'DELETE'}).then(async response => { if (!response.ok) throw await readApiError(response); });
      await loadStoryboardEditor(taskId); return;
    }
    if (button.dataset.storyboardAction === 'asset-save') {
      const item = button.closest('[data-placement-item]');
      await requestJson(`/api/tasks/${taskId}/storyboard/assets/${button.dataset.placementId}`, {
        method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
          position:item.querySelector('[name="placementPosition"]').value,
          cutoutApplied:item.querySelector('[name="placementCutout"]').checked,
          instruction:item.querySelector('[name="placementInstruction"]').value,
          startOffsetSeconds:Number(item.querySelector('[name="placementStart"]').value || 0),
          endOffsetSeconds:item.querySelector('[name="placementEnd"]').value === '' ? null : Number(item.querySelector('[name="placementEnd"]').value),
          scalePercent:Number(item.querySelector('[name="placementScale"]').value || 38),
          animation:item.querySelector('[name="placementAnimation"]').value,
          zIndex:Number(item.querySelector('[name="placementZIndex"]').value || 0),
          volumePercent:Number(item.querySelector('[name="placementVolume"]')?.value || 48),
          fadeInSeconds:Number(item.querySelector('[name="placementFadeIn"]')?.value || 0),
          fadeOutSeconds:Number(item.querySelector('[name="placementFadeOut"]')?.value || 0)
        })
      });
      await loadStoryboardEditor(taskId); return;
    }
    if (button.dataset.storyboardAction === 'move') {
      await requestJson(`/api/tasks/${taskId}/storyboard/segments/${button.dataset.clipIndex}/move`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body:JSON.stringify({direction:button.dataset.direction})
      });
      await loadStoryboardEditor(taskId);
      return;
    }
    const card = button.closest('[data-storyboard-segment]');
    if (button.dataset.storyboardAction === 'asset-ai' || button.dataset.storyboardAction === 'asset-manual') {
      const assetId = card.querySelector('[name="assetId"]').value;
      if (!assetId) throw new Error('请先选择一个本地素材');
      await requestJson(`/api/tasks/${taskId}/storyboard/segments/${button.dataset.clipIndex}/assets`, {
        method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
          assetId, instruction:card.querySelector('[name="assetInstruction"]').value,
          aiAssign:button.dataset.storyboardAction === 'asset-ai'
        })
      });
      await loadStoryboardEditor(taskId); return;
    }
    if (button.dataset.storyboardAction === 'rewrite') {
      await requestJson(`/api/tasks/${taskId}/script/segments/${button.dataset.clipIndex}/regenerate`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body:JSON.stringify({instruction:card.querySelector('[name="rewriteInstruction"]').value})
      });
      await loadStoryboardEditor(taskId);
      return;
    }
    await saveStoryboardSegment(taskId, card, retry => {
      button.textContent = `检测到后台更新，自动重试 ${retry} / 3…`;
    });
    button.textContent = '已保存';
    setTimeout(() => { button.textContent = '保存此分镜'; button.disabled = false; }, 1000);
  } catch (error) {
    button.disabled = false;
    button.title = error.message;
    if (button.dataset.storyboardAction === 'director-review-apply') {
      button.textContent = '再次应用评审结论';
      const trace = error.traceId ? `\n追踪号：${error.traceId}` : '';
      window.alert(`评审结论没有应用成功，现有分镜未被修改。\n\n你可以直接再次点击“应用到时间线”；如果仍然失败，再打开诊断日志。${trace}`);
    } else if (error.code === 'CONCURRENT_MODIFICATION') {
      const activity = await describeTaskActivity(taskId);
      button.textContent = button.dataset.storyboardAction === 'save-all-continue'
        ? '保存被后台更新打断，点击重试 →' : '再次保存此分镜';
      window.alert(`保存连续 3 次遇到后台更新，当前编辑内容已保留，没有重新加载。\n\n${activity}\n你可以等待几秒后直接再次点击保存。${error.traceId ? `\n追踪号：${error.traceId}` : ''}`);
    } else {
      window.alert(error.message);
    }
  }
}

async function saveAllStoryboardSegments(taskId, progressButton = null) {
  const cards = [...storyboardWorkspace.querySelectorAll('[data-storyboard-segment]')];
  if (progressButton) progressButton.textContent = `正在保存 0 / ${cards.length}…`;
  for (let index = 0; index < cards.length; index++) {
    const card = cards[index];
    await saveStoryboardSegment(taskId, card, retry => {
      if (progressButton) progressButton.textContent = `第 ${index + 1} 个分镜遇到后台更新，自动重试 ${retry} / 3…`;
    });
    if (progressButton) progressButton.textContent = `正在保存 ${index + 1} / ${cards.length}…`;
  }
}

function storyboardSegmentPayload(card) {
  return {
    startSeconds:Number(card.querySelector('[name="startSeconds"]').value),
    endSeconds:Number(card.querySelector('[name="endSeconds"]').value),
    narration:card.querySelector('[name="narration"]').value,
    subtitle:card.querySelector('[name="subtitle"]').value,
    effectCue:card.querySelector('[name="effectCue"]').value,
    locked:card.querySelector('[name="locked"]').checked,
    excluded:card.querySelector('[name="excluded"]').checked
  };
}

async function saveStoryboardSegment(taskId, card, onRetry = null) {
  const clipIndex = card.dataset.storyboardSegment;
  const body = JSON.stringify(storyboardSegmentPayload(card));
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      return await requestJson(`/api/tasks/${taskId}/storyboard/segments/${clipIndex}`, {
        method:'PUT', headers:{'Content-Type':'application/json'}, body
      });
    } catch (error) {
      if (error.code !== 'CONCURRENT_MODIFICATION' || attempt === 2) throw error;
      onRetry?.(attempt + 1);
      await new Promise(resolve => setTimeout(resolve, 180 * (attempt + 1)));
    }
  }
}

async function describeTaskActivity(taskId) {
  try {
    const task = await requestJson(`/api/tasks/${taskId}`);
    const running = task.stages?.find(stage => stage.status === 'RUNNING');
    if (running) return `后台当前正在执行：${stageNames[running.type] || running.type}（${running.progress || 0}%）`;
    if (task.status === 'WAITING_REVIEW') return '后台当前状态：等待分镜确认，没有运行中的处理阶段。';
    return `后台当前状态：${task.status || '未知'}，没有运行中的处理阶段。可能是另一个页面或刚结束的后台写入。`;
  } catch (statusError) {
    return `无法读取后台当前阶段：${statusError.rawMessage || statusError.message}`;
  }
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw await readApiError(response);
  return response.json();
}

function renderedVideoSection(task) {
  if (!task.renderedVideoPath) return '';
  const previewUrl = `/api/tasks/${task.id}/preview`;
  const downloadUrl = `/api/tasks/${task.id}/output`;
  const sizeMb = (task.renderedFileSizeBytes / 1024 / 1024).toFixed(1);
  return `<section class="detail-block rendered-video">
    <div class="rendered-video-head">
      <div><h3>最终成片</h3><p>视频已完成，可以直接在线播放或导出到电脑（${sizeMb} MB）。</p></div>
      <span>COMPLETED</span>
    </div>
    <video class="result-player" controls preload="metadata" playsinline src="${previewUrl}">
      当前浏览器不支持 HTML5 视频播放，请使用下方导出按钮。
    </video>
    <div class="render-actions">
      <a class="preview-button" href="${previewUrl}" target="_blank" rel="noopener">新窗口预览</a>
      <a class="download-button" href="${downloadUrl}" download>导出 MP4</a>
      <button class="preview-button" type="button" data-add-project-asset="${task.id}">加入素材库</button>
    </div>
    <code title="${escapeHtml(task.renderedVideoPath)}">${escapeHtml(task.renderedVideoPath)}</code>
  </section>`;
}

function artifactSection(task) {
  const artifacts = [
    ['提取音频', task.extractedAudioPath],
    ['场景清单', task.sceneManifestPath],
    ['转写文本', task.transcriptTextPath],
    ['字幕文件', task.subtitlePath],
    ['转写数据', task.transcriptJsonPath],
    ['画面分析', task.visualAnalysisPath],
    ['高光清单', task.highlightManifestPath]
    ,['生成文案', task.generatedScriptPath]
    ,['配音清单', task.voiceManifestPath]
    ,['剪辑时间线', task.timelinePath]
    ,['中文字幕', task.generatedSubtitlePath]
    ,['最终成片', task.renderedVideoPath]
  ].filter(([, path]) => path);
  if (!artifacts.length) return '';
  return `<section class="detail-block"><h3>已生成产物</h3><div class="artifact-list">${
    artifacts.map(([label, path]) => `<div><span>${label}</span><code title="${escapeHtml(path)}">${escapeHtml(path)}</code></div>`).join('')
  }</div></section>`;
}

function formatDate(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit'
  }).format(new Date(value));
}

function currentStageText(task) {
  if (task.status === 'WAITING_REVIEW') return '等待检查 AI 分镜和文案，确认后继续生成';
  if (task.status === 'CANCELLED') return '任务已取消，已完成的阶段和工程数据仍保留';
  if (task.status === 'FAILED') return `处理失败：${task.failureReason || '请查看后端日志'}`;
  const running = task.stages.find(stage => stage.status === 'RUNNING');
  if (running?.type === 'VOICE_GENERATION') return `正在配音：${running.progress}%`;
  if (running) return `正在执行：${stageNames[running.type]}（${running.progress}%）`;
  const pending = task.stages.find(stage => stage.status === 'PENDING');
  if (pending?.errorMessage) return pending.errorMessage;
  if (pending) return `下一阶段：${stageNames[pending.type]}`;
  return '全部阶段已完成';
}

function formatDuration(totalSeconds) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = Math.floor(totalSeconds % 60);
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
}

function escapeHtml(value) {
  const node = document.createElement('div');
  node.textContent = value ?? '';
  return node.innerHTML;
}

function createTaskWithProgress(requestBody) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open('POST', '/api/tasks');
    request.responseType = 'json';

    request.upload.addEventListener('progress', event => {
      if (!event.lengthComputable) {
        message.textContent = '正在上传视频…';
        return;
      }
      const percent = Math.round((event.loaded / event.total) * 100);
      const loadedMb = (event.loaded / 1024 / 1024).toFixed(1);
      const totalMb = (event.total / 1024 / 1024).toFixed(1);
      message.textContent = `正在上传：${percent}%（${loadedMb} / ${totalMb} MB）`;
    });

    request.addEventListener('load', () => {
      if (request.status >= 200 && request.status < 300) {
        resolve(request.response);
        return;
      }
      const payload = request.response || {};
      reject(Object.assign(new Error(payload.message || `请求失败：HTTP ${request.status}`), {
        code: payload.code,
        suggestion: payload.suggestion,
        traceId: payload.traceId || request.getResponseHeader('X-Trace-Id'),
        status: request.status
      }));
    });
    request.addEventListener('error', () => reject(Object.assign(
      new Error('网络连接中断，视频未能完整上传'),
      { suggestion: '确认后端仍在运行，然后重新提交任务' }
    )));
    request.send(requestBody);
  });
}

async function readApiError(response) {
  const traceId = response.headers.get('X-Trace-Id');
  try {
    const payload = await response.json();
    const rawMessage = payload.message || `请求失败：HTTP ${response.status}`;
    return Object.assign(new Error(formatGuidedError(rawMessage, payload.suggestion, payload.traceId || traceId)), {
      rawMessage,
      code: payload.code,
      suggestion: payload.suggestion,
      category: payload.category,
      retryable: payload.retryable,
      action: payload.action,
      traceId: payload.traceId || traceId,
      status: response.status
    });
  } catch (parseError) {
    return Object.assign(new Error(`请求失败：HTTP ${response.status}`), {
      traceId, status: response.status, cause: parseError
    });
  }
}

function formatGuidedError(message, suggestion, traceId) {
  const parts = [message || '操作失败'];
  if (suggestion && !parts[0].includes(suggestion)) parts.push(`解决建议：${suggestion}`);
  if (traceId) parts.push(`追踪号：${traceId}`);
  return parts.join('；');
}

function taskFailureGuidance(message) {
  const text = String(message || '');
  if (/(磁盘|空间不足|no space|insufficient storage)/i.test(text)) return '请在“诊断日志”中检查存储空间，清理临时文件后重试。';
  if (/(ffmpeg|ffprobe|编码器|encoder)/i.test(text)) return '请打开“诊断日志”检查 FFmpeg 与硬件编码器，再重试失败阶段。';
  if (/(ollama|模型|model|whisper|piper)/i.test(text)) return '请确认本地模型服务和依赖已启动，可在“诊断日志”查看详细状态。';
  if (/(timeout|timed out|超时|connection|连接|网络)/i.test(text)) return '请检查网络或本地服务状态，稍后重试失败阶段。';
  if (/(登录|认证|cookie|unauthorized|forbidden)/i.test(text)) return '请重新登录或更新内容平台授权后再试。';
  return '可先重试失败阶段；若仍失败，请打开“诊断日志”并按追踪号定位原因。';
}

function taskFailureHtml(message) {
  return `<div class="task-error"><strong>${escapeHtml(message)}</strong><small>解决建议：${escapeHtml(taskFailureGuidance(message))}</small></div>`;
}

function showLoadError(error) {
  console.error('[GameNarrator] 状态刷新失败', error);
  taskList.innerHTML = `<p class="empty">任务加载失败：${escapeHtml(error.message)}</p>`;
}

loadTasks().catch(showLoadError);

const guideSteps = [
  {selector: '.hero', title: '先认识完整工作流', text: '从录像到成片依次经过素材读取、镜头检测、字幕或 Whisper 转写、画面理解、分镜、高光与文案、配音、时间轴和渲染。平台字幕存在时会优先使用，AI 与自动素材都可以独立关闭。'},
  {selector: '.create-panel', title: '第 1 步：创建剪辑任务', text: '填写任务名称、游戏类型、解说风格和创作要求，选择完整视频或精彩片段，再上传本地视频。首次测试建议保留“分镜后暂停”，便于在渲染前检查文案、字幕和镜头。'},
  {selector: '.effect-toggle', title: '第 2 步：按需组合 AI 能力', text: '自动流程、云端视觉、AI 文案、AI 配音和自动素材互不绑定。关闭某项不会阻止手动编辑；使用云端服务前请先到“设置”填写 API Key，本地模型则复用已安装的 Ollama、Whisper 与 Piper。'},
  {selector: '.dropzone', title: '第 3 步：上传并开始处理', text: '支持 MP4、MOV、MKV 和 WEBM。创建后请勿关闭正在运行的桌面应用；任务进度会通过实时事件推送，不需要反复刷新页面。'},
  {selector: '.task-panel', title: '第 4 步：右侧跟踪运行任务', text: '运行中的任务固定显示在右侧，包括当前阶段、阶段完成数、处理范围、百分比和九阶段轨迹。点击绿色任务卡可以随时打开详情；任务结束后会自动离开右侧。'},
  {selector: '.history-panel', title: '第 5 步：从最左侧历史继续', text: '只有真正生成完成的任务才会进入页面最左侧“最近完成”列表。处理中、等待检查、失败或取消的任务都留在右侧，避免被误认为已经完成。点击已完成条目可查看生成文件、分镜、文案、时间线和最终视频。'},
  {selector: '.storyboard-review-option', title: '第 6 步：检查分镜再继续', text: '开启分镜检查后，流程会在文案与分镜生成后暂停。进入线性分镜工作台可调整顺序、起止时间、字幕、解说、素材和特效；保存全部修改后再继续配音与渲染。'},
  {selector: '.primary-nav', title: '更多工具入口', text: '“镜头搜索”使用本地语义模型寻找片段；“平台导入”负责下载并创建项目；“素材库”管理授权素材；“设置”管理云端或本地 AI。遇到问题可点击右上角“诊断日志”。'},
  {selector: '.topbar-actions', title: '完成、诊断与再次查看', text: '任务完成后在详情中预览并导出 MP4。任何阶段失败时先查看任务详情和诊断日志；本引导可以随时从“使用引导”重新打开。当前版本为 v2.2.28。'}
];
const guideStepViews = new Map([
  ['.hero', 'studio'],
  ['.create-panel', 'studio'],
  ['.effect-toggle', 'studio'],
  ['.dropzone', 'studio'],
  ['.task-panel', 'studio'],
  ['.history-panel', 'studio'],
  ['.storyboard-review-option', 'studio'],
  ['.primary-nav', 'studio'],
  ['.topbar-actions', 'studio']
]);
guideSteps.splice(guideSteps.length - 1, 0,
  {view: 'search', selector: '.segment-search-panel', title: '镜头搜索', text: '这里可以用自然语言搜索已经完成分析的镜头。引导进入本步骤时会自动切换到“镜头搜索”页面。'},
  {view: 'import', selector: '.media-importer', title: '平台导入', text: '这里用于解析并导入本人创作、已获授权或平台允许下载的媒体。引导会等待导入功能加载后再定位。'},
  {view: 'assets', selector: '.asset-library', title: '素材库', text: '这里管理开放许可素材和本人已授权的素材，并保留来源与授权信息。'},
  {view: 'settings', selector: '.ai-settings-panel', title: 'AI 与系统设置', text: '这里配置本地或云端 AI、模型和 API Key。敏感密钥不会在使用引导中展示。'}
);
let guideIndex = 0;
let guideTarget = null;
let guideRoot = null;
let guideRequestId = 0;

function saveGuideState(value) {
  try {
    localStorage.setItem('game-narrator-guide-v4', value);
  } catch (_) { /* Private browsing may disable storage; the guide still works. */ }
}

function ensureGuideRoot() {
  if (guideRoot) return guideRoot;
  guideRoot = document.createElement('dialog');
  guideRoot.className = 'guided-tour';
  guideRoot.setAttribute('aria-labelledby', 'guided-tour-title');
  guideRoot.setAttribute('aria-describedby', 'guided-tour-text');
  guideRoot.hidden = true;
  guideRoot.innerHTML = `<div class="guided-tour-shade" aria-hidden="true"></div>
    <aside class="guided-tour-card">
      <header><span data-guide-count></span><button type="button" data-guide-close aria-label="关闭使用引导">×</button></header>
      <h2 id="guided-tour-title"></h2><p id="guided-tour-text"></p>
      <div class="guided-tour-dots" aria-hidden="true"></div>
      <footer><button type="button" data-guide-previous>上一步</button><button type="button" data-guide-skip>稍后再看</button><button type="button" data-guide-next>下一步</button></footer>
    </aside>`;
  document.body.appendChild(guideRoot);
  guideRoot.addEventListener('cancel', event => {
    event.preventDefault();
    closeGuide(false);
  });
  guideRoot.querySelector('[data-guide-close]').addEventListener('click', () => closeGuide(false));
  guideRoot.querySelector('[data-guide-skip]').addEventListener('click', () => closeGuide(false));
  guideRoot.querySelector('[data-guide-previous]').addEventListener('click', () => showGuideStep(guideIndex - 1));
  guideRoot.querySelector('[data-guide-next]').addEventListener('click', () => {
    if (guideIndex === guideSteps.length - 1) closeGuide(true);
    else if (!guideTarget) showGuideStep(guideIndex);
    else showGuideStep(guideIndex + 1);
  });
  return guideRoot;
}

function positionGuideCard() {
  if (!guideRoot || guideRoot.hidden || !guideTarget) return;
  const card = guideRoot.querySelector('.guided-tour-card');
  const rect = guideTarget.getBoundingClientRect();
  const margin = 18;
  const cardWidth = Math.min(380, window.innerWidth - 24);
  card.style.width = `${cardWidth}px`;
  const cardHeight = card.offsetHeight;
  let left = rect.right + margin;
  let top = rect.top;
  if (left + cardWidth > window.innerWidth - 12) left = rect.left - cardWidth - margin;
  if (left < 12) {
    left = Math.min(Math.max(12, rect.left), window.innerWidth - cardWidth - 12);
    top = rect.bottom + margin;
    if (top + cardHeight > window.innerHeight - 12) top = rect.top - cardHeight - margin;
  }
  card.style.left = `${Math.max(12, Math.min(left, window.innerWidth - cardWidth - 12))}px`;
  card.style.top = `${Math.max(12, Math.min(top, window.innerHeight - cardHeight - 12))}px`;
}

function waitForGuideLayout() {
  return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
}

async function prepareGuideStep(step) {
  const view = step.view || guideStepViews.get(step.selector) || 'studio';
  if (document.body.dataset.view !== view) {
    activateView(view, false);
    history.replaceState({view}, '', `/?view=${view}`);
  }
  await ensureViewScripts(view);
  await waitForGuideLayout();
  const target = document.querySelector(step.selector);
  if (target) {
    let parent = target.parentElement;
    while (parent) {
      if (parent.tagName === 'DETAILS') parent.open = true;
      parent = parent.parentElement;
    }
  }
  return target;
}

async function showGuideStep(index) {
  const requestId = ++guideRequestId;
  const root = ensureGuideRoot();
  guideIndex = Math.max(0, Math.min(index, guideSteps.length - 1));
  const step = guideSteps[guideIndex];
  guideTarget?.classList.remove('guided-tour-target');
  guideTarget = null;
  try {
    guideTarget = await prepareGuideStep(step);
  } catch (error) {
    console.error('[GameNarrator] 使用引导页面加载失败', error);
  }
  if (requestId !== guideRequestId) return;
  if (!guideTarget) {
    root.hidden = false;
    if (!root.open) root.showModal();
    root.querySelector('[data-guide-count]').textContent = `${guideIndex + 1} / ${guideSteps.length}`;
    root.querySelector('#guided-tour-title').textContent = step.title;
    root.querySelector('#guided-tour-text').textContent = `${step.text}\n\n当前页面暂时无法显示该位置，请完成页面加载后重试本步骤。`;
    root.querySelector('[data-guide-previous]').disabled = guideIndex === 0;
    root.querySelector('[data-guide-next]').textContent = guideIndex === guideSteps.length - 1 ? '完成引导' : '重试本步骤';
    root.querySelector('.guided-tour-dots').innerHTML = guideSteps.map((_, position) => `<i class="${position === guideIndex ? 'active' : ''}"></i>`).join('');
    root.querySelector('.guided-tour-card').style.left = '50%';
    root.querySelector('.guided-tour-card').style.top = '50%';
    root.querySelector('.guided-tour-card').style.transform = 'translate(-50%, -50%)';
    return;
  }
  root.hidden = false;
  if (!root.open) root.showModal();
  root.querySelector('[data-guide-count]').textContent = `${guideIndex + 1} / ${guideSteps.length}`;
  root.querySelector('#guided-tour-title').textContent = step.title;
  root.querySelector('#guided-tour-text').textContent = step.text;
  root.querySelector('[data-guide-previous]').disabled = guideIndex === 0;
  root.querySelector('[data-guide-next]').textContent = guideIndex === guideSteps.length - 1 ? '完成引导' : '下一步';
  root.querySelector('.guided-tour-dots').innerHTML = guideSteps.map((_, position) => `<i class="${position === guideIndex ? 'active' : ''}"></i>`).join('');
  guideTarget.classList.add('guided-tour-target');
  guideTarget.scrollIntoView({behavior: 'smooth', block: 'center'});
  root.querySelector('.guided-tour-card').style.transform = '';
  setTimeout(() => {
    if (requestId !== guideRequestId) return;
    positionGuideCard();
    root.querySelector('[data-guide-next]').focus({preventScroll: true});
  }, 320);
}

function openGuide() {
  showGuideStep(0);
}

function closeGuide(completed) {
  guideRequestId += 1;
  guideTarget?.classList.remove('guided-tour-target');
  guideTarget = null;
  if (guideRoot) {
    if (guideRoot.open) guideRoot.close();
    guideRoot.hidden = true;
  }
  saveGuideState(completed ? 'completed' : 'dismissed');
  document.querySelector('#guide-open')?.focus({preventScroll: true});
}

document.querySelector('#guide-open')?.addEventListener('click', openGuide);
window.addEventListener('resize', positionGuideCard);
window.addEventListener('scroll', positionGuideCard, {passive: true});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && guideRoot && !guideRoot.hidden) closeGuide(false);
});
// Account access is injected so the same Web bundle works unchanged in the Windows EXE shell.
(function initializeAccountCenter() {
  const actions = document.querySelector('.topbar-actions');
  if (!actions) return;
  const button = document.createElement('button');
  button.className = 'diagnostics-open';
  button.type = 'button';
  button.textContent = '匿名使用';
  actions.prepend(button);
  const dialog = document.createElement('dialog');
  dialog.className = 'task-dialog';
  dialog.innerHTML = `<div class="dialog-shell"><header class="dialog-header"><div><small>ACCOUNT</small><h2>账号与云端数据</h2></div><button class="dialog-close" type="button" aria-label="关闭账号窗口">×</button></header><section class="diagnostics-content"><p data-account-state>匿名模式：项目和素材保存在当前电脑。</p><form data-account-form><label>用户名<input name="username" autocomplete="username" required></label><label>密码<input name="password" type="password" autocomplete="current-password" required></label><label data-register-only hidden>显示名称<input name="displayName" autocomplete="name" maxlength="80"></label><label class="remember-login"><input name="rememberMe" type="checkbox" value="true" checked> 在这台电脑上保持登录 30 天</label><div class="diagnostics-actions"><button type="submit" data-account-submit>登录</button><button type="button" data-register>注册新账号</button><button type="button" data-anonymous>切换匿名</button><a data-admin href="/admin.html" hidden>后台管理</a></div></form><p data-account-tip role="status" aria-live="polite"></p></section></div>`;
  document.body.append(dialog);
  const state = dialog.querySelector('[data-account-state]'), tip = dialog.querySelector('[data-account-tip]');
  async function request(url, options={}) { const response=await fetch(url,{headers:{'Content-Type':'application/json'},...options}); const body=await response.json(); if(!response.ok) throw Error(body.message||'操作失败'); return body; }
  async function refresh() { const me=await request('/api/auth/me'); button.textContent=me.authenticated?me.displayName:'匿名使用'; state.textContent=me.authenticated?`已登录：${me.displayName}（${me.role}），当前项目按账号隔离保存。`:'匿名模式：项目和素材仅保存在当前电脑。'; dialog.querySelector('[data-admin]').hidden=me.role!=='ADMIN'; }
  button.onclick=()=>{refresh().catch(()=>{});dialog.showModal()}; dialog.querySelector('.dialog-close').onclick=()=>dialog.close();
  let registrationMode=false;
  const form=dialog.querySelector('[data-account-form]'), registerOnly=form.querySelector('[data-register-only]'), passwordInput=form.elements.password, submitButton=form.querySelector('[data-account-submit]'), registerButton=form.querySelector('[data-register]');
  function setRegistrationMode(enabled){registrationMode=enabled;registerOnly.hidden=!enabled;registerOnly.querySelector('input').required=enabled;passwordInput.minLength=enabled?8:0;passwordInput.autocomplete=enabled?'new-password':'current-password';submitButton.textContent=enabled?'创建账号并登录':'登录';registerButton.textContent=enabled?'返回登录':'注册新账号';tip.textContent=enabled?'请填写用户名、至少 8 位密码和显示名称。':'';}
  form.onsubmit=async event=>{event.preventDefault();tip.textContent='';try{const data=Object.fromEntries(new FormData(form));if(!registrationMode)delete data.displayName;await request(registrationMode?'/api/auth/register':'/api/auth/login',{method:'POST',body:JSON.stringify(data)});location.reload()}catch(error){tip.textContent=error.message}};
  registerButton.onclick=()=>{setRegistrationMode(!registrationMode);(registrationMode?registerOnly.querySelector('input'):form.elements.username).focus()};
  dialog.querySelector('[data-anonymous]').onclick=async()=>{await request('/api/auth/anonymous',{method:'POST'});location.reload()};
  refresh().catch(()=>{});
})();
