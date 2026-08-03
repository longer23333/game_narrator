(() => {
  const dialog = document.querySelector('#diagnostics-dialog');
  const output = document.querySelector('#diagnostics-log');
  if (!dialog || !output) return;
  async function refresh() {
    output.textContent = '正在读取日志…';
    try {
      const response = await fetch('/api/debug/logs?lines=400', {cache:'no-store'});
      if (!response.ok) throw new Error(`日志读取失败（HTTP ${response.status}）`);
      output.textContent = await response.text() || '当前没有日志记录。';
      output.scrollTop = output.scrollHeight;
    } catch (error) { output.textContent = error.message; }
  }
  document.querySelector('#diagnostics-open')?.addEventListener('click', () => {
    if (!dialog.open) dialog.showModal();
    refresh();
  });
  document.querySelector('#diagnostics-close')?.addEventListener('click', () => dialog.close());
  document.querySelector('#diagnostics-refresh')?.addEventListener('click', refresh);
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  const report = (level, message, context) => fetch('/api/debug/client-events', {
    method:'POST', headers:{'Content-Type':'application/json'}, keepalive:true,
    body:JSON.stringify({level, message:String(message || '未知错误').slice(0,1000), context})
  }).catch(() => {});
  window.addEventListener('error', event => report('ERROR', event.message, 'window.error'));
  window.addEventListener('unhandledrejection', event => report('ERROR', event.reason?.message || event.reason, 'unhandledrejection'));
  window.gameNarratorDiagnosticEvent = (message, context='frontend') => report('WARN', message, context);
})();
