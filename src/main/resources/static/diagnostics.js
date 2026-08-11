(() => {
  const dialog = document.querySelector('#diagnostics-dialog');
  const output = document.querySelector('#diagnostics-log');
  const storageOutput = document.querySelector('#storage-report');
  if (!dialog || !output) return;
  async function refresh() {
    output.textContent = '正在读取日志…';
    try {
      const taskId = document.querySelector('#diagnostics-open')?.dataset.taskId || '';
      const query = new URLSearchParams({lines:'400'});
      if (taskId) query.set('taskId', taskId);
      const response = await fetch(`/api/debug/logs?${query}`, {cache:'no-store'});
      if (!response.ok) throw new Error(`日志读取失败（HTTP ${response.status}）`);
      const scope = taskId ? `任务 ${taskId} 的相关日志\n\n` : '';
      output.textContent = scope + (await response.text() || '当前没有日志记录。');
      output.scrollTop = output.scrollHeight;
    } catch (error) { output.textContent = error.message; }
    try {
      const response = await fetch('/api/admin/storage', {cache:'no-store'});
      if (!response.ok) throw new Error(`存储状态读取失败：HTTP ${response.status}`);
      const data = await response.json();
      const gb = value => (Number(value || 0) / 1024 / 1024 / 1024).toFixed(2);
      storageOutput.textContent = `存储目录：${data.storagePath}\n文件系统：${data.filesystemType}\n总容量：${gb(data.totalBytes)} GB\n可用容量：${gb(data.usableBytes)} GB\n安全保留：${gb(data.reservedBytes)} GB\n源视频：${gb(data.managedSourceBytes)} GB / ${data.sourceCount} 个\n已登记产物：${gb(data.artifactBytes)} GB / ${data.artifactCount} 个\n\n最大源视频：\n${(data.largestSources || []).map(item => `${gb(item.sizeBytes)} GB  ${item.taskName}  ${item.path}`).join('\n') || '无'}`;
    } catch (error) { if (storageOutput) storageOutput.textContent = error.message; }
  }
  document.querySelector('#diagnostics-open')?.addEventListener('click', () => {
    if (!dialog.open) dialog.showModal();
    refresh();
  });
  dialog.addEventListener('close', () => {
    const openButton = document.querySelector('#diagnostics-open');
    if (openButton) delete openButton.dataset.taskId;
  });
  document.querySelector('#diagnostics-close')?.addEventListener('click', () => dialog.close());
  document.querySelector('#diagnostics-refresh')?.addEventListener('click', refresh);
  document.querySelector('#storage-cleanup')?.addEventListener('click', async event => {
    event.target.disabled = true;
    try { await fetch('/api/admin/storage/cleanup', {method:'POST'}); await refresh(); }
    finally { event.target.disabled = false; }
  });
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  const report = (level, message, context) => fetch('/api/debug/client-events', {
    method:'POST', headers:{'Content-Type':'application/json'}, keepalive:true,
    body:JSON.stringify({level, message:String(message || '未知错误').slice(0,1000), context})
  }).catch(() => {});
  window.addEventListener('error', event => report('ERROR', event.message, 'window.error'));
  window.addEventListener('unhandledrejection', event => report('ERROR', event.reason?.message || event.reason, 'unhandledrejection'));
  window.gameNarratorDiagnosticEvent = (message, context='frontend') => report('WARN', message, context);
})();
