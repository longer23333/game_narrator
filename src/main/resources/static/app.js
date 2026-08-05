const taskList = document.querySelector('#task-list');
const activeTaskPanel = document.querySelector('#active-task');
const validViews = new Set(['studio', 'search', 'import', 'assets', 'settings']);

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
const aiSettingsForm = document.querySelector('#ai-settings-form');
const aiKeyState = document.querySelector('#ai-key-state');
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
const taskForm = document.querySelector('#task-form');
const message = document.querySelector('#form-message');
const detailDialog = document.querySelector('#task-detail-dialog');
const detailTitle = document.querySelector('#detail-title');
const detailContent = document.querySelector('#detail-content');
const storyboardDialog = document.querySelector('#storyboard-dialog');
const storyboardWorkspace = document.querySelector('#storyboard-workspace');
let activeTaskId = null;
let tasksLoading = false;
let taskEventStream = null;
let taskStreamConnected = false;
let taskStreamRetryMs = 1000;
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
  if (tasksLoading) return;
  tasksLoading = true;
  console.debug('[GameNarrator] GET /api/tasks');
  try {
    const response = await fetch('/api/tasks');
    if (!response.ok) throw await readApiError(response);
    const tasks = await response.json();
    reconcileTaskCards(tasks);
    return tasks;
  } finally {
    tasksLoading = false;
  }
}

function scheduleTaskPoll(delayMs) {
  // Kept as a compatibility hook for older action handlers. Task state is SSE-only.
}

function connectTaskStream() {
  if (!window.EventSource || taskEventStream) return;
  taskEventStream = new EventSource('/api/tasks/stream');
  taskEventStream.onopen = () => {
    taskStreamConnected = true;
    taskStreamRetryMs = 1000;
    clearInterval(storyboardProgressTimer);
  };
  taskEventStream.onmessage = event => {
    try {
      const tasks = JSON.parse(event.data);
      reconcileTaskCards(tasks);
      window.dispatchEvent(new CustomEvent('gamenarrator:tasks', {detail:tasks}));
    } catch (error) {
      console.warn('[GameNarrator] SSE payload ignored', error);
    }
  };
  taskEventStream.onerror = () => {
    taskStreamConnected = false;
    taskEventStream?.close();
    taskEventStream = null;
    const retry = taskStreamRetryMs;
    taskStreamRetryMs = Math.min(30000, taskStreamRetryMs * 2);
    setTimeout(connectTaskStream, retry);
  };
}

function taskCardHtml(task) {
  return `
      <div class="task-head"><strong>${escapeHtml(task.name)}</strong><div class="task-card-actions"><span>${task.status}</span><button type="button" class="task-card-rename" data-rename-list-task="${task.id}" data-task-name="${escapeHtml(task.name)}">重命名</button><button type="button" class="task-card-delete" data-delete-list-task="${task.id}" data-task-name="${escapeHtml(task.name)}" aria-label="删除任务 ${escapeHtml(task.name)}">删除</button></div></div>
      <div class="tags"><i>${escapeHtml(task.gameCategory)}</i><i>${task.editingScope === 'HIGHLIGHTS' ? '精彩片段' : '完整视频'}</i><i>${escapeHtml(formatDate(task.createdAt))}</i></div>
      ${task.failureReason ? `<div class="task-error">${escapeHtml(task.failureReason)}</div>` : ''}
      <div class="stage-line">${task.stages.map(stage =>
        `<span class="${stage.status.toLowerCase()}" title="${escapeHtml(stageTitle(stage))}"></span>`
      ).join('')}</div>
      <div class="stage-caption">${escapeHtml(currentStageText(task))}</div>`;
}

function createTaskCard(task) {
  const card = document.createElement('div');
  card.className = 'task-card';
  card.dataset.taskId = task.id;
  card.setAttribute('role', 'button');
  card.setAttribute('tabindex', '0');
  card.setAttribute('aria-label', `查看任务 ${task.name} 的详情`);
  card.innerHTML = taskCardHtml(task);
  return card;
}

function reconcileTaskCards(tasks) {
  const activeTasks = tasks.filter(task => task.status !== 'COMPLETED');
  renderActiveTask(activeTasks);
  const recentTasks = tasks.filter(task => task.status === 'COMPLETED').slice(0, 8);
  if (!recentTasks.length) {
    const empty = taskList.querySelector('.empty');
    if (empty && taskList.children.length === 1) empty.textContent = '还没有已完成的任务。';
    else taskList.innerHTML = '<p class="empty">还没有已完成的任务。</p>';
    return;
  }
  taskList.querySelector('.empty')?.remove();
  const incomingIds = new Set(recentTasks.map(task => task.id));
  taskList.querySelectorAll('.task-card').forEach(card => {
    if (!incomingIds.has(card.dataset.taskId)) card.remove();
  });
  recentTasks.forEach(task => {
    let card = Array.from(taskList.children).find(item => item.dataset?.taskId === task.id);
    if (card) updateTaskCard(card, task);
    else card = createTaskCard(task);
    taskList.appendChild(card);
  });
}

function renderActiveTask(activeTasks) {
  if (!activeTasks.length) {
    activeTaskPanel.innerHTML = '<p class="empty">当前没有正在进行的任务</p>';
    return;
  }
  activeTaskPanel.innerHTML = `<small>ACTIVE QUEUE · ${activeTasks.length}</small>${activeTasks.map(task => {
    const running = task.stages.find(stage => stage.status === 'RUNNING');
    const progress = running?.progress ?? (task.status === 'WAITING_REVIEW' ? 100 : 0);
    const completedStages = task.stages.filter(stage => stage.status === 'COMPLETED').length;
    return `<div class="active-task-item"><button type="button" class="active-task-card" data-open-active-task="${task.id}">
    <span><strong>${escapeHtml(task.name)}</strong><i>${escapeHtml(task.status)}</i></span>
    <span class="active-task-meta"><i>${escapeHtml(task.gameCategory)}</i><i>${task.editingScope === 'HIGHLIGHTS' ? '精彩片段' : '完整视频'}</i><i>${completedStages} / ${task.stages.length} 阶段</i></span>
    <b>${escapeHtml(currentStageText(task))}</b>
    <span class="active-progress"><i style="width:${Math.max(0, Math.min(100, progress))}%"></i></span>
    <span class="active-stage-line">${task.stages.map(stage => `<i class="${stage.status.toLowerCase()}" title="${escapeHtml(stageTitle(stage))}"></i>`).join('')}</span>
    ${running?.type === 'VOICE_GENERATION' ? `<em>${escapeHtml(voiceProgressText(task, running))}</em>` : ''}
    ${running?.type === 'RENDERING' ? `<em>${escapeHtml(renderingProgressText(task, running))}</em>` : ''}
    ${missingToolGuidance(task) ? `<em class="tool-guidance">${escapeHtml(missingToolGuidance(task))}</em>` : ''}
  </button>${task.status === 'PROCESSING' ? `<button type="button" class="task-cancel" data-cancel-task="${task.id}">取消当前任务</button>` : ''}</div>`;
  }).join('')}`;
}

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

function missingToolGuidance(task) {
  const reason = task.stages.find(stage => stage.status === 'PENDING' && stage.errorMessage)?.errorMessage || '';
  if (/whisper/i.test(reason)) return '缺少 Whisper：请运行 .\\scripts\\setup-whisper.ps1，完成后任务会自动重试。';
  if (/piper/i.test(reason)) return '缺少 Piper：请运行 .\\scripts\\setup-piper.ps1，完成后任务会自动重试。';
  return '';
}

function updateTaskCard(card, task) {
  card.setAttribute('aria-label', `查看任务 ${task.name} 的详情`);
  card.querySelector('.task-head strong').textContent = task.name;
  card.querySelectorAll('[data-task-name]').forEach(button => { button.dataset.taskName = task.name; });
  card.querySelector('.task-head span').textContent = task.status;
  let error = card.querySelector('.task-error');
  if (task.failureReason) {
    if (!error) {
      error = document.createElement('div');
      error.className = 'task-error';
      card.querySelector('.tags').insertAdjacentElement('afterend', error);
    }
    error.textContent = task.failureReason;
  } else {
    error?.remove();
  }

  const stageLine = card.querySelector('.stage-line');
  if (stageLine.children.length !== task.stages.length) {
    stageLine.innerHTML = task.stages.map(stage => '<span></span>').join('');
  }
  task.stages.forEach((stage, index) => {
    const marker = stageLine.children[index];
    marker.className = stage.status.toLowerCase();
    marker.title = stageTitle(stage);
  });
  card.querySelector('.stage-caption').textContent = currentStageText(task);
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
    const traceHint = error.traceId ? `（追踪号：${error.traceId}）` : '';
    const suggestion = error.suggestion ? `；建议：${error.suggestion}` : '';
    message.textContent = `${error.message || '创建失败'}${traceHint}${suggestion}`;
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
  button.disabled = true;
  button.textContent = '正在终止进程…';
  try {
    await requestJson(`/api/tasks/${button.dataset.cancelTask}/cancel`, {method:'POST'});
    await loadTasks();
    if (activeTaskId === button.dataset.cancelTask) await refreshTaskDetails(activeTaskId);
  } catch (error) {
    button.disabled = false;
    button.textContent = '取消当前任务';
    window.alert(`取消失败：${error.message}`);
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
  if (!window.confirm(`确定删除任务“${button.dataset.taskName}”吗？任务记录、源视频、输出视频和 data 中的处理文件都会永久删除。`)) return;
  button.disabled = true;
  button.textContent = '删除中…';
  try {
    const response = await fetch(`/api/tasks/${button.dataset.deleteListTask}`, {method:'DELETE'});
    if (!response.ok) throw await readApiError(response);
    button.closest('.task-card')?.remove();
    await loadTasks();
  } catch (error) {
    button.disabled = false;
    button.textContent = '删除';
    button.title = error.message;
  }
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

detailContent.addEventListener('input', event => {
  if (event.target.matches('[name="voiceSpeed"]')) {
    event.target.closest('label')?.querySelector('output').replaceChildren(`${Number(event.target.value).toFixed(2)}×`);
  } else if (event.target.matches('[name="intensity"]')) {
    event.target.closest('label')?.querySelector('output').replaceChildren(`${Math.round(Number(event.target.value) * 100)}%`);
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
        soundEffects:values.get('soundEffects') === 'on'
      })
    });
    if (!response.ok) throw await readApiError(response);
    status.textContent = '特效重渲染已启动，可在处理流水线查看进度。';
    button.textContent = '渲染进行中';
    scheduleTaskPoll(1000);
    setTimeout(() => refreshTaskDetails(form.dataset.effectSettings), 1200);
  } catch (error) {
    button.disabled = false;
    button.textContent = '应用特效并重新渲染';
    status.textContent = error.message;
  }
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
    if (actionButton.dataset.scriptAction === 'save') {
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
          speed: Number(card.querySelector('[name="voiceSpeed"]').value)
        })
      });
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
    <section class="detail-block task-operations"><button type="button" data-rename-task="${task.id}" data-task-name="${escapeHtml(task.name)}">重命名任务</button><small>只修改显示名称，不影响正在处理的阶段和已有文件。</small></section>
    <section class="detail-block task-operations task-delete-operation"><button type="button" data-delete-task="${task.id}" data-task-name="${escapeHtml(task.name)}">删除任务及数据</button><small>同时删除任务记录、源视频、输出视频及 data 中的全部处理文件；不可撤销。</small></section>
    ${task.status === 'PROCESSING' ? `<section class="detail-block task-operations"><button type="button" data-cancel-task="${task.id}">取消当前任务</button><small>立即终止当前外部进程，保留已完成阶段，清理未完成的临时文件。</small></section>` : ''}
    ${['FAILED','CANCELLED'].includes(task.status) ? `<section class="detail-block task-operations"><button type="button" data-retry-task="${task.id}">${task.status === 'CANCELLED' ? '从取消处继续' : '重试失败阶段'}</button><small>已完成阶段会保留，从中断位置继续处理。</small></section>` : ''}
    ${task.generatedScriptPath ? `<section class="detail-block task-operations storyboard-launch"><button type="button" data-open-storyboard="${task.id}">进入线性分镜工作台 →</button><small>${task.storyboardReviewEnabled && !task.storyboardApproved ? '需要在独立分镜时间线中检查并确认后才能继续生成。' : '按镜头顺序编辑画面、起止时间、文案、字幕、素材和特效。'}</small></section>` : ''}
    ${task.generatedScriptPath ? `<section class="detail-block task-operations"><button type="button" data-open-script="${task.id}">编辑分段文案</button><small>支持保存、AI 单段重写和单段重新配音。</small></section>` : ''}
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
    ${task.failureReason ? `<section class="detail-block"><h3>失败原因</h3><div class="task-error">${escapeHtml(task.failureReason)}</div></section>` : ''}
    <section class="detail-block">
      <h3>处理流水线</h3>
      <div class="stage-details">${task.stages.map(stage => `
        <article class="stage-row ${stage.status.toLowerCase()}">
          <span class="stage-index">${String(stage.sequence).padStart(2, '0')}</span>
          <div class="stage-info"><strong>${stageNames[stage.type]}</strong><small>${stage.status}${stage.errorMessage ? ` · ${escapeHtml(stage.errorMessage)}` : ''}</small></div>
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
  if (task.timelinePath) refreshRenderPreview(task.id);
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
      <label class="effect-toggle"><input name="dynamicSubtitles" type="checkbox" checked>启用动态 ASS 字幕主题</label>
      <label class="effect-toggle"><input name="soundEffects" type="checkbox">加入冲击、转场和喜剧提示音</label>
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
  if (!window.confirm(`确定删除任务“${button.dataset.taskName}”吗？任务记录、源视频、输出视频和 data 中的处理文件都会永久删除。`)) return;
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
    button.textContent = '删除任务';
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
  const [script, voices, manualReviews] = await Promise.all([
    requestJson(`/api/tasks/${taskId}/script`),
    requestJson(`/api/tasks/${taskId}/voice/options`),
    requestJson(`/api/tasks/${taskId}/script/reviews`)
  ]);
  const voiceOptions = voices.map(voice => `<option value="${escapeHtml(voice.id)}" ${voice.available ? '' : 'disabled'} ${voice.defaultVoice ? 'selected' : ''}>${escapeHtml(voice.name)}${voice.available ? '' : '（未安装）'}</option>`).join('');
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
          <div class="voice-controls"><label>配音音色<select name="voiceId">${voiceOptions}</select></label><label>语速<input name="voiceSpeed" type="range" min="0.5" max="2" step="0.05" value="1"><output>1.00×</output></label></div>
          <div class="script-actions">
            <button type="button" data-script-action="save" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">保存片段</button>
            <button type="button" data-script-action="regenerate" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">AI 重写</button>
            <button type="button" data-script-action="voice" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">重新配音</button>
            <button type="button" data-script-action="review-approved" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">通过</button>
            <button type="button" data-script-action="review-needs-changes" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">需修改</button>
          </div>
          <label>评审备注<input name="reviewNote" maxlength="500" value="${escapeHtml(manual.note || '')}" placeholder="说明事实冲突、节奏或措辞问题"></label>
        </article>`}).join('')}</div>
    </section>`);
  detailContent.querySelector('.script-editor').scrollIntoView({behavior: 'smooth', block: 'start'});
}

async function loadStoryboardEditor(taskId) {
  clearInterval(storyboardProgressTimer);
  if (!storyboardDialog.open) storyboardDialog.showModal();
  storyboardWorkspace.innerHTML = '<p class="empty">正在读取完整分镜时间线…</p>';
  const [storyboard, localAssets, placements, editorTimeline, waveform] = await Promise.all([
    requestJson(`/api/tasks/${taskId}/storyboard`),
    requestJson('/api/assets?importStatus=DOWNLOADED&limit=100'),
    requestJson(`/api/tasks/${taskId}/storyboard/assets`),
    requestJson(`/api/tasks/${taskId}/editor`),
    requestJson(`/api/tasks/${taskId}/editor/waveform?points=320`)
  ]);
  const totalDuration = storyboard.segments.reduce((sum, item) => sum + item.endSeconds - item.startSeconds, 0);
  storyboardWorkspace.innerHTML = `
    <section class="detail-block storyboard-editor" data-task-id="${taskId}" data-review-enabled="${storyboard.reviewEnabled}" data-approved="${storyboard.approved}">
      <header class="storyboard-editor-head"><div><small>EDITOR WORKSPACE</small><h3>${escapeHtml(storyboard.title || '自由剪辑与分镜')}</h3><p>${escapeHtml(storyboard.synopsis || '')}</p></div>
      <div class="storyboard-head-actions"><button type="button" data-storyboard-action="auto-assets" data-task-id="${taskId}">自动匹配并下载素材</button>${storyboard.approved ? '<span class="storyboard-approved">已确认 / 自动模式</span>' : '<span class="storyboard-review-pending">修改后请使用底部主按钮保存并继续</span>'}</div></header>
      <div class="storyboard-stats"><span>${storyboard.segments.length} 个分镜</span><span>预计素材时长 ${formatDuration(totalDuration)}</span><span>支持拖拽排序与入点/出点修剪</span></div>
      <section class="editor-source-monitor"><video controls preload="metadata" src="/api/tasks/${taskId}/source" data-editor-preview></video><div><strong>源视频监视器</strong><small>在时间线上选择位置会同步跳转原片；可直接播放确认剪切点。</small></div></section>
      <section class="storyboard-visual-timeline" data-visual-timeline>
        <header><div><strong>自由剪辑时间线</strong><small>单击选择片段或定位播放头；支持分割、删除、拖动排序和双侧修剪</small></div><div class="timeline-toolbar"><button type="button" data-editor-history="UNDO">撤销</button><button type="button" data-editor-history="REDO">重做</button><button type="button" data-editor-action="SPLIT" disabled>刀片分割</button><button type="button" data-editor-action="DELETE" disabled>删除片段</button><label>缩放 <input type="range" min="24" max="120" value="54" data-timeline-zoom></label><b data-history-count></b></div></header>
        <div class="timeline-selection-status" data-timeline-selection>请选择片段；点击片段内部可设置分割位置</div>
        <div class="storyboard-waveform" data-storyboard-waveform></div>
        <div class="storyboard-track-scroll"><div class="storyboard-track" data-storyboard-track></div></div>
      </section>
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
            <div class="storyboard-placement-list">${placements.filter(item => item.clipIndex === segment.clipIndex).map(item => `<div class="storyboard-placement-item" data-placement-item="${item.id}"><strong>${escapeHtml(item.title)}</strong><small>${item.assetType === 'VIDEO' ? '自动剪切' : item.assetType === 'MEME' ? '裁切适配' : item.assetType === 'BGM' ? '背景混音' : '事件混音'}</small><select name="placementPosition">${['TOP_LEFT','TOP_RIGHT','CENTER','BOTTOM_LEFT','BOTTOM_RIGHT','FULL_SCREEN','AUDIO_TRACK'].map(position => `<option value="${position}" ${item.position === position ? 'selected' : ''}>${position}</option>`).join('')}</select><label><input name="placementCutout" type="checkbox" ${item.cutoutApplied ? 'checked' : ''}>抠图/透明叠加</label><input name="placementInstruction" value="${escapeHtml(item.instruction || '')}" placeholder="素材处理说明"><button type="button" data-storyboard-action="asset-save" data-task-id="${taskId}" data-placement-id="${item.id}">保存素材设置</button><button type="button" data-storyboard-action="asset-remove" data-task-id="${taskId}" data-placement-id="${item.id}">移除</button></div>`).join('') || '<small>尚未添加额外素材</small>'}</div>
          </section>
          <div class="storyboard-actions"><button type="button" data-storyboard-action="save" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">保存此分镜</button><button type="button" data-storyboard-action="rewrite" data-task-id="${taskId}" data-clip-index="${segment.clipIndex}">AI 重写此镜</button></div>
        </article>`).join('')}</div>
      <footer class="storyboard-continue-bar"><div><strong>修改完成了吗？</strong><small>点击后会先保存全部分镜，再明确启动配音、时间线规划和视频渲染。</small></div><button type="button" data-storyboard-action="save-all-continue" data-task-id="${taskId}">保存全部修改并执行下一步 →</button></footer>
    </section>`;
  mountStoryboardTimeline(taskId, editorTimeline, waveform);
  storyboardWorkspace.scrollTo({top:0, behavior:'smooth'});
  await updateStoryboardProgress(taskId);
  if (!taskStreamConnected) storyboardProgressTimer = setInterval(() => updateStoryboardProgress(taskId), 2000);
}

function mountStoryboardTimeline(taskId, timeline, waveform) {
  const panel = storyboardWorkspace.querySelector('[data-visual-timeline]');
  if (!panel) return;
  panel._timeline = timeline;
  panel._waveform = waveform;
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
      scheduleTaskPoll(1000);
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
          instruction:item.querySelector('[name="placementInstruction"]').value
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
    await requestJson(`/api/tasks/${taskId}/storyboard/segments/${button.dataset.clipIndex}`, {
      method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
        startSeconds:Number(card.querySelector('[name="startSeconds"]').value),
        endSeconds:Number(card.querySelector('[name="endSeconds"]').value),
        narration:card.querySelector('[name="narration"]').value,
        subtitle:card.querySelector('[name="subtitle"]').value,
        effectCue:card.querySelector('[name="effectCue"]').value,
        locked:card.querySelector('[name="locked"]').checked,
        excluded:card.querySelector('[name="excluded"]').checked
      })
    });
    button.textContent = '已保存';
    setTimeout(() => { button.textContent = '保存此分镜'; button.disabled = false; }, 1000);
  } catch (error) {
    button.disabled = false;
    button.title = error.message;
    if (error.code === 'CONCURRENT_MODIFICATION' || error.status === 409) {
      window.alert('任务已被后台流程或其他编辑操作更新，正在重新加载分镜，请确认后再重试。');
      await loadStoryboardEditor(taskId);
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
    await requestJson(`/api/tasks/${taskId}/storyboard/segments/${card.dataset.storyboardSegment}`, {
      method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
        startSeconds:Number(card.querySelector('[name="startSeconds"]').value),
        endSeconds:Number(card.querySelector('[name="endSeconds"]').value),
        narration:card.querySelector('[name="narration"]').value,
        subtitle:card.querySelector('[name="subtitle"]').value,
        effectCue:card.querySelector('[name="effectCue"]').value,
        locked:card.querySelector('[name="locked"]').checked,
        excluded:card.querySelector('[name="excluded"]').checked
      })
    });
    if (progressButton) progressButton.textContent = `正在保存 ${index + 1} / ${cards.length}…`;
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
    return Object.assign(new Error(payload.message || `请求失败：HTTP ${response.status}`), {
      code: payload.code,
      suggestion: payload.suggestion,
      traceId: payload.traceId || traceId,
      status: response.status
    });
  } catch (parseError) {
    return Object.assign(new Error(`请求失败：HTTP ${response.status}`), {
      traceId, status: response.status, cause: parseError
    });
  }
}

function showLoadError(error) {
  console.error('[GameNarrator] 状态刷新失败', error);
  taskList.innerHTML = `<p class="empty">任务加载失败：${escapeHtml(error.message)}</p>`;
}

loadTasks().catch(error => {
  showLoadError(error);
  scheduleTaskPoll(10000);
}).finally(connectTaskStream);

const guideSteps = [
  {selector: '.hero', title: '先认识完整工作流', text: '从录像到成片依次经过素材读取、镜头检测、字幕或 Whisper 转写、画面理解、分镜、高光与文案、配音、时间轴和渲染。平台字幕存在时会优先使用，AI 与自动素材都可以独立关闭。'},
  {selector: '.create-panel', title: '第 1 步：创建剪辑任务', text: '填写任务名称、游戏类型、解说风格和创作要求，选择完整视频或精彩片段，再上传本地视频。首次测试建议保留“分镜后暂停”，便于在渲染前检查文案、字幕和镜头。'},
  {selector: '.effect-toggle', title: '第 2 步：按需组合 AI 能力', text: '自动流程、云端视觉、AI 文案、AI 配音和自动素材互不绑定。关闭某项不会阻止手动编辑；使用云端服务前请先到“设置”填写 API Key，本地模型则复用已安装的 Ollama、Whisper 与 Piper。'},
  {selector: '.dropzone', title: '第 3 步：上传并开始处理', text: '支持 MP4、MOV、MKV 和 WEBM。创建后请勿关闭正在运行的桌面应用；任务进度会通过实时事件推送，不需要反复刷新页面。'},
  {selector: '.task-panel', title: '第 4 步：右侧跟踪运行任务', text: '运行中的任务固定显示在右侧，包括当前阶段、阶段完成数、处理范围、百分比和九阶段轨迹。点击绿色任务卡可以随时打开详情；任务结束后会自动离开右侧。'},
  {selector: '.history-panel', title: '第 5 步：从最左侧历史继续', text: '只有真正生成完成的任务才会进入页面最左侧“最近完成”列表。处理中、等待检查、失败或取消的任务都留在右侧，避免被误认为已经完成。点击已完成条目可查看生成文件、分镜、文案、时间线和最终视频。'},
  {selector: '.storyboard-review-option', title: '第 6 步：检查分镜再继续', text: '开启分镜检查后，流程会在文案与分镜生成后暂停。进入线性分镜工作台可调整顺序、起止时间、字幕、解说、素材和特效；保存全部修改后再继续配音与渲染。'},
  {selector: '.primary-nav', title: '更多工具入口', text: '“镜头搜索”使用本地语义模型寻找片段；“平台导入”负责下载并创建项目；“素材库”管理授权素材；“设置”管理云端或本地 AI。遇到问题可点击右上角“诊断日志”。'},
  {selector: '.topbar-actions', title: '完成、诊断与再次查看', text: '任务完成后在详情中预览并导出 MP4。任何阶段失败时先查看任务详情和诊断日志；本引导可以随时从“使用引导”重新打开。当前版本为 v1.6.3。'}
];
let guideIndex = 0;
let guideTarget = null;
let guideRoot = null;

function guideStorage(action, value) {
  try {
    if (action === 'get') return localStorage.getItem('game-narrator-guide-v4');
    localStorage.setItem('game-narrator-guide-v4', value);
  } catch (_) { return null; }
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

function showGuideStep(index) {
  const root = ensureGuideRoot();
  guideIndex = Math.max(0, Math.min(index, guideSteps.length - 1));
  const step = guideSteps[guideIndex];
  guideTarget?.classList.remove('guided-tour-target');
  guideTarget = document.querySelector(step.selector);
  if (!guideTarget) {
    if (guideIndex < guideSteps.length - 1) return showGuideStep(guideIndex + 1);
    return closeGuide(true);
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
  requestAnimationFrame(() => {
    positionGuideCard();
    root.querySelector('[data-guide-next]').focus({preventScroll: true});
  });
}

function openGuide() {
  showGuideStep(0);
}

function closeGuide(completed) {
  guideTarget?.classList.remove('guided-tour-target');
  guideTarget = null;
  if (guideRoot) {
    if (guideRoot.open) guideRoot.close();
    guideRoot.hidden = true;
  }
  guideStorage('set', completed ? 'completed' : 'dismissed');
  document.querySelector('#guide-open')?.focus({preventScroll: true});
}

document.querySelector('#guide-open')?.addEventListener('click', openGuide);
window.addEventListener('resize', positionGuideCard);
window.addEventListener('scroll', positionGuideCard, {passive: true});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && guideRoot && !guideRoot.hidden) closeGuide(false);
});
if (!guideStorage('get')) setTimeout(openGuide, 700);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) scheduleTaskPoll(0);
});
