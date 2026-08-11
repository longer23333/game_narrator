const stageNames = {VIDEO_INGESTION:'素材读取',SCENE_DETECTION:'镜头检测与音频提取',TRANSCRIPTION:'语音转写',VIDEO_UNDERSTANDING:'画面理解',HIGHLIGHT_SELECTION:'完整分镜与高光标注',SCRIPT_GENERATION:'文案生成',VOICE_GENERATION:'AI 配音',TIMELINE_PLANNING:'时间轴规划',RENDERING:'视频合成'};
export const formatDate = value => new Intl.DateTimeFormat('zh-CN',{dateStyle:'short',timeStyle:'short'}).format(new Date(value));
export const stageTitle = stage => `${stageNames[stage.type] || stage.type}：${stage.status}${stage.errorMessage ? `；${stage.errorMessage}` : ''}`;
export function currentStageText(task) {
  const current = task.stages.find(stage => stage.status === 'RUNNING') || task.stages.find(stage => stage.status === 'FAILED');
  if (current) return `${stageNames[current.type] || current.type} · ${current.progress || 0}%`;
  if (task.status === 'WAITING_REVIEW') return '等待确认分镜';
  return task.status === 'COMPLETED' ? '已完成' : task.status;
}
export function voiceProgressText(task, stage) {
  const total=Math.max(1,task.generatedScriptSegmentCount||1); const completed=Math.min(total,Math.max(0,Math.floor((Math.max(10,stage.progress)-10)/85*total)));
  return `配音进度：约 ${completed} / ${total} 段 · ${stage.progress}%（逐段生成后自动进入合成）`;
}
export function renderingProgressText(task, stage) {
  const total=Math.max(1,task.generatedScriptSegmentCount||task.selectedHighlightCount||1); const current=Math.min(total,Math.max(1,Math.floor(Math.max(0,Math.min(1,(stage.progress-10)/65))*total)+1));
  return stage.progress>=75?`片段 ${total}/${total} 已编码 · 正在合成成片 · ${stage.progress}%`:`正在编码片段 ${current}/${total} · ${stage.progress}%`;
}
export function missingToolGuidance(task) {
  const reason=task.stages.find(stage=>stage.status==='PENDING'&&stage.errorMessage)?.errorMessage||'';
  if(/whisper/i.test(reason))return '缺少 Whisper：请运行 .\\scripts\\setup-whisper.ps1，完成后任务会自动重试。';
  if(/piper/i.test(reason))return '缺少 Piper：请运行 .\\scripts\\setup-piper.ps1，完成后任务会自动重试。'; return '';
}
