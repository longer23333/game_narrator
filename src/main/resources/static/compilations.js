/**
 * 剪辑合集：把不同任务里的镜头挑出来组成一个命名合集，并调整播放顺序。
 * 对应后端 /api/compilations（list / find / create / add / reorder）。
 */
(() => {
  const list = document.querySelector('#compilation-list');
  const detail = document.querySelector('#compilation-detail');
  const createForm = document.querySelector('#compilation-create-form');
  const message = document.querySelector('#compilation-message');
  if (!list || !detail) return;

  const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);

  async function request(url, options = {}) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `请求失败（HTTP ${response.status}）`);
    return body;
  }

  const json = (method, payload) => ({
    method, headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload)
  });

  const timestamp = value => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', {hour12:false});
  };

  let compilations = [];
  let selectedId = null;
  let tasks = [];
  let tasksLoaded = false;
  const segmentsByTask = new Map();

  async function ensureTasks() {
    if (tasksLoaded) return tasks;
    tasks = await request('/api/tasks');
    tasksLoaded = true;
    return tasks;
  }

  async function segmentsOf(taskId) {
    if (!segmentsByTask.has(taskId)) {
      let segments = [];
      try {
        const storyboard = await request(`/api/tasks/${taskId}/storyboard`);
        segments = Array.isArray(storyboard.segments) ? storyboard.segments : [];
      } catch (error) {
        // 任务还没生成分镜时会 404，这不是致命错误，只是暂时没有可选镜头。
        segments = [];
      }
      segmentsByTask.set(taskId, segments);
    }
    return segmentsByTask.get(taskId);
  }

  function current() {
    return compilations.find(item => item.id === selectedId) || null;
  }

  function renderList() {
    if (!compilations.length) {
      list.innerHTML = '<p class="empty compact">还没有合集。先在上面输入名称并创建。</p>';
      return;
    }
    list.innerHTML = compilations.map(item => `
      <button type="button" class="compilation-chip${item.id === selectedId ? ' active' : ''}"
              data-select-compilation="${escapeHtml(item.id)}">
        <b>${escapeHtml(item.name)}</b>
        <small>${item.items.length} 个切片 · ${escapeHtml(timestamp(item.createdAt))}</small>
      </button>`).join('');
  }

  function renderDetail() {
    const item = current();
    if (!item) {
      detail.innerHTML = '<p class="empty compact">选择左侧的一个合集，或先新建一个。</p>';
      return;
    }
    const total = item.items.length;
    const rows = item.items.map((entry, index) => `
      <li class="compilation-item" data-item-id="${escapeHtml(entry.id)}">
        <span><b>${index + 1}. ${escapeHtml(entry.taskName || '未知任务')}</b>
          <small>第 ${entry.clipIndex} 镜</small></span>
        <div>
          <button type="button" data-move="up" ${index === 0 ? 'disabled' : ''}>↑ 上移</button>
          <button type="button" data-move="down" ${index === total - 1 ? 'disabled' : ''}>↓ 下移</button>
        </div>
      </li>`).join('');
    detail.innerHTML = `
      <header class="compilation-head">
        <div><h3>${escapeHtml(item.name)}</h3><small>创建于 ${escapeHtml(timestamp(item.createdAt))} · 共 ${total} 个切片</small></div>
      </header>
      ${total ? `<ol class="compilation-items">${rows}</ol>`
              : '<p class="empty compact">这个合集还没有切片。用下面的表单把某个任务的镜头加进来。</p>'}
      <form id="compilation-add-form">
        <label>任务<select name="taskId" required><option value="">选择任务…</option>
          ${tasks.map(task => `<option value="${escapeHtml(task.id)}">${escapeHtml(task.name || '未命名任务')}</option>`).join('')}
        </select></label>
        <label>镜头<select name="clipIndex" required><option value="">先选择上面的任务</option></select></label>
        <button type="submit">加入合集</button>
      </form>
      <p class="compilation-hint">镜头编号与「分镜工作台」里每个镜头标注的编号一致。合集只做挑选与排序，不会再触发一次渲染。</p>`;
  }

  async function refresh(preferredId) {
    compilations = await request('/api/compilations');
    const wanted = preferredId || selectedId;
    selectedId = compilations.some(item => item.id === wanted) ? wanted : (compilations[0]?.id || null);
    renderList();
    renderDetail();
  }

  async function fillSegments(taskId, select) {
    select.innerHTML = '<option value="">正在读取该任务的分镜…</option>';
    const segments = await segmentsOf(taskId);
    if (!segments.length) {
      select.innerHTML = '<option value="">该任务还没有可用的分镜</option>';
      return;
    }
    select.innerHTML = segments.map(segment => {
      const description = String(segment.description || segment.narration || '').trim();
      const label = `第 ${segment.clipIndex} 镜${description ? ' · ' + description.slice(0, 24) : ''}`;
      return `<option value="${segment.clipIndex}">${escapeHtml(label)}</option>`;
    }).join('');
  }

  createForm?.addEventListener('submit', async event => {
    event.preventDefault();
    const input = createForm.elements.name;
    const name = String(input.value || '').trim();
    if (!name) return;
    const button = createForm.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const created = await request('/api/compilations', json('POST', {name}));
      message.textContent = `已创建合集「${created.name}」。`;
      input.value = '';
      await refresh(created.id);
    } catch (error) {
      message.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });

  list.addEventListener('click', async event => {
    const button = event.target.closest('[data-select-compilation]');
    if (!button) return;
    selectedId = button.dataset.selectCompilation;
    renderList();
    renderDetail();
  });

  detail.addEventListener('change', async event => {
    const select = event.target.closest('form#compilation-add-form [name="taskId"]');
    if (!select) return;
    const clipSelect = detail.querySelector('form#compilation-add-form [name="clipIndex"]');
    if (!select.value) {
      clipSelect.innerHTML = '<option value="">先选择上面的任务</option>';
      return;
    }
    try {
      await fillSegments(select.value, clipSelect);
    } catch (error) {
      clipSelect.innerHTML = '<option value="">读取分镜失败</option>';
      message.textContent = error.message;
    }
  });

  detail.addEventListener('submit', async event => {
    if (event.target.id !== 'compilation-add-form') return;
    event.preventDefault();
    const item = current();
    if (!item) return;
    const form = event.target;
    const taskId = String(form.elements.taskId.value || '');
    const clipIndex = Number(form.elements.clipIndex.value || 0);
    if (!taskId || !clipIndex) {
      message.textContent = '请先选择任务和要加入的镜头。';
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const updated = await request(`/api/compilations/${item.id}/items`, json('POST', {taskId, clipIndex}));
      message.textContent = `已把第 ${clipIndex} 镜加入「${updated.name}」。`;
      await refresh(updated.id);
    } catch (error) {
      message.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });

  detail.addEventListener('click', async event => {
    const button = event.target.closest('[data-move]');
    if (!button) return;
    const item = current();
    if (!item || button.disabled) return;
    const itemId = button.closest('[data-item-id]')?.dataset.itemId;
    if (!itemId) return;
    const order = item.items.map(entry => entry.id);
    const from = order.indexOf(itemId);
    const to = button.dataset.move === 'up' ? from - 1 : from + 1;
    if (from < 0 || to < 0 || to >= order.length) return;
    [order[from], order[to]] = [order[to], order[from]];
    button.disabled = true;
    try {
      // 后端要求提交合集中的全部切片 id，顺序即最终顺序。
      const updated = await request(`/api/compilations/${item.id}/order`, json('PUT', {itemIds:order}));
      const index = compilations.findIndex(entry => entry.id === updated.id);
      if (index >= 0) compilations[index] = updated;
      renderDetail();
    } catch (error) {
      message.textContent = error.message;
      button.disabled = false;
    }
  });

  (async () => {
    try {
      await ensureTasks();
      await refresh();
    } catch (error) {
      message.textContent = error.message;
      list.innerHTML = '<p class="empty compact">合集列表读取失败，请刷新页面重试。</p>';
    }
  })();
})();
