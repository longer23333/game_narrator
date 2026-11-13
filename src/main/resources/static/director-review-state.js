export async function runDirectorReview({board, button, start, refresh}) {
  const oldLabel = button.textContent;
  const previous = [...board.children].filter(node => node.tagName !== 'HEADER');
  const record = board.querySelector('.director-runtime small');
  if (record) previous.push(record);
  const visibility = previous.map(node => [node, node.hidden]);
  previous.forEach(node => { node.hidden = true; });
  const progress = board.ownerDocument.createElement('p');
  progress.setAttribute('role', 'status');
  progress.textContent = '本次评审正在生成四个角色的意见和总导演结论；下方历史结果已暂时收起。';
  board.appendChild(progress);
  board.setAttribute('aria-busy', 'true');
  button.disabled = true;
  button.textContent = '本次 AI 评审中…';
  let failure;
  try { await start(); } catch (error) { failure = error; }
  try { await refresh(); } catch (error) { failure ||= error; }
  finally {
    progress.remove();
    visibility.forEach(([node, hidden]) => { node.hidden = hidden; });
    board.removeAttribute('aria-busy');
    button.disabled = false;
    button.textContent = oldLabel;
  }
  if (failure) throw failure;
}
