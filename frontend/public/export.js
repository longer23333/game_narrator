(() => {
  const detail = document.querySelector('#detail-content');
  if (!detail) return;

  const observer = new MutationObserver(() => {
    const actions = detail.querySelector('.render-actions');
    if (!actions || actions.querySelector('.open-export')) return;
    const preview = actions.querySelector('a[href*="/preview"]');
    const match = preview?.getAttribute('href')?.match(/\/api\/tasks\/([^/]+)\/preview/);
    if (!match) return;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'download-button open-export';
    button.textContent = '导出设置';
    button.dataset.taskId = match[1];
    button.addEventListener('click', () => showPanel(actions, match[1]));
    actions.append(button);
    const effectButton = document.createElement('button');
    effectButton.type = 'button';
    effectButton.className = 'preview-button rerender-effects';
    effectButton.textContent = '应用特效重新渲染';
    effectButton.addEventListener('click', () => showEffectSettings(actions, match[1]));
    actions.append(effectButton);
  });
  observer.observe(detail, {childList: true, subtree: true});

  async function showPanel(actions, taskId) {
    let panel = actions.parentElement.querySelector('.export-panel');
    if (!panel) {
      panel = document.createElement('div');
      panel.className = 'export-panel';
      actions.after(panel);
    }
    panel.innerHTML = '<p class="empty compact">正在读取导出预设…</p>';
    try {
      const [presets, jobs] = await Promise.all([
        getJson('/api/export-presets'),
        getJson(`/api/tasks/${taskId}/exports`)
      ]);
      panel.innerHTML = `
        <div class="export-grid">
          <label>导出预设<select data-field="preset">${presets.map(p =>
            `<option value="${p.id}">${text(p.name)} · ${p.container}/${p.videoCodec}</option>`).join('')}</select></label>
          <label>文件名称<input data-field="name" maxlength="120" value="GameNarrator-${taskId.slice(0, 8)}"></label>
          <label>分辨率<select data-field="size"><option value="">跟随预设</option><option value="1280x720">1280×720</option><option value="1920x1080">1920×1080</option><option value="2560x1440">2560×1440</option><option value="3840x2160">3840×2160</option></select></label>
          <label>帧率<select data-field="fps"><option value="">跟随预设/源视频</option><option value="24">24 FPS</option><option value="30">30 FPS</option><option value="60">60 FPS</option></select></label>
          <label>视频质量<select data-field="quality"><option value="">跟随预设</option><option value="18">高质量</option><option value="23">平衡</option><option value="28">较小文件</option></select></label>
          <label>字幕<select data-field="subtitle"><option value="">跟随预设</option><option value="SOFT">可开关字幕</option><option value="BURN_IN">烧录字幕</option><option value="NONE">不含字幕</option><option value="SEPARATE_SRT">单独字幕</option></select></label>
        </div>
        <div class="export-toolbar"><button type="button" class="submit-export">开始导出</button><span class="export-message"></span></div>
        <div class="export-jobs">${jobsHtml(jobs)}</div>`;
      panel.querySelector('.submit-export').addEventListener('click', () => submit(panel, taskId));
    } catch (error) {
      panel.innerHTML = `<div class="task-error">${text(error.message)}</div>`;
    }
  }

  async function showEffectSettings(actions, taskId) {
    let panel = actions.parentElement.querySelector('.effect-settings-panel');
    if (panel) {
      panel.hidden = !panel.hidden;
      return;
    }
    panel = document.createElement('div');
    panel.className = 'export-panel effect-settings-panel';
    panel.innerHTML = '<p class="empty compact">正在读取特效预设…</p>';
    actions.after(panel);
    try {
      const presets = await getJson('/api/effect-presets');
      panel.innerHTML = `<div class="export-grid">
        <label>创作风格<select data-effect="preset">${presets.map(item =>
          `<option value="${item.code}">${text(item.name)} · ${text(item.description)}</option>`).join('')}</select></label>
        <label>效果强度<input data-effect="intensity" type="range" min="0" max="1" step="0.05" value="0.78"><output>78%</output></label>
        <label class="effect-toggle"><input data-effect="subtitles" type="checkbox" checked> 启用动态字幕</label>
        <label class="effect-toggle"><input data-effect="sounds" type="checkbox"> 启用程序化音效轨道（冲击、掠过、喜剧提示）</label>
      </div>
      <p class="effect-note">预设会同时控制视觉特效、转场范围、字幕主题、单片段最大效果数和原声音量。</p>
      <div class="export-toolbar"><button type="button" class="apply-effects">应用并重新渲染</button><span class="effect-message"></span></div>`;
      const slider = panel.querySelector('[data-effect=intensity]');
      slider.addEventListener('input', () => slider.nextElementSibling.textContent = `${Math.round(slider.value * 100)}%`);
      panel.querySelector('.apply-effects').addEventListener('click',
        event => rerenderEffects(event.currentTarget, panel, taskId));
    } catch (error) {
      panel.innerHTML = `<div class="task-error">${text(error.message)}</div>`;
    }
  }

  async function rerenderEffects(button, panel, taskId) {
    button.disabled = true;
    const message = panel.querySelector('.effect-message');
    message.textContent = '正在启动特效渲染…';
    const payload = {
      presetCode: panel.querySelector('[data-effect=preset]').value,
      intensity: Number(panel.querySelector('[data-effect=intensity]').value),
      dynamicSubtitles: panel.querySelector('[data-effect=subtitles]').checked,
      soundEffects: panel.querySelector('[data-effect=sounds]').checked
    };
    try {
      const response = await fetch(`/api/tasks/${taskId}/rerender-effects`, {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error((await response.json()).message || `HTTP ${response.status}`);
      message.textContent = '后台渲染中，可在处理阶段查看进度。';
      setTimeout(() => {
        button.disabled = false;
        message.textContent = '渲染完成后刷新任务即可查看。';
      }, 8000);
    } catch (error) {
      button.disabled = false;
      message.textContent = `启动失败：${error.message}`;
    }
  }

  async function submit(panel, taskId) {
    const size = panel.querySelector('[data-field=size]').value.split('x');
    const value = field => panel.querySelector(`[data-field=${field}]`).value;
    const payload = {
      presetId: value('preset'),
      exportName: value('name'),
      width: size[0] ? Number(size[0]) : null,
      height: size[1] ? Number(size[1]) : null,
      frameRate: value('fps') ? Number(value('fps')) : null,
      qualityValue: value('quality') ? Number(value('quality')) : null,
      targetBitrateKbps: null,
      subtitleMode: value('subtitle') || null
    };
    panel.querySelector('.export-message').textContent = '已提交，正在后台导出…';
    try {
      const response = await fetch(`/api/tasks/${taskId}/exports`, {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error((await response.json()).message || `HTTP ${response.status}`);
      poll(panel, taskId);
    } catch (error) {
      panel.querySelector('.export-message').textContent = `导出失败：${error.message}`;
    }
  }

  async function poll(panel, taskId) {
    if (!panel.isConnected) return;
    const jobs = await getJson(`/api/tasks/${taskId}/exports`);
    panel.querySelector('.export-jobs').innerHTML = jobsHtml(jobs);
    if (jobs.some(job => ['PENDING', 'RUNNING'].includes(job.status))) {
      setTimeout(() => poll(panel, taskId), 2000);
    } else {
      panel.querySelector('.export-message').textContent = '导出任务已完成';
    }
  }

  function jobsHtml(jobs) {
    if (!jobs.length) return '<p class="empty compact">暂无导出记录</p>';
    return jobs.map(job => `<article class="export-job ${job.status.toLowerCase()}">
      <div><strong>${text(job.exportName)}.${job.container.toLowerCase()}</strong>
      <small>${text(job.presetName)} · ${job.status} · ${job.progress}%${job.outputSizeBytes ? ` · ${(job.outputSizeBytes / 1048576).toFixed(1)} MB` : ''}</small></div>
      ${job.status === 'COMPLETED' ? `<a class="download-button" href="/api/exports/${job.id}/download">下载</a>` : ''}
      ${job.errorMessage ? `<span class="task-error">${text(job.errorMessage)}</span>` : ''}
    </article>`).join('');
  }

  async function getJson(url) {
    const response = await fetch(url);
    if (!response.ok) throw new Error((await response.json()).message || `HTTP ${response.status}`);
    return response.json();
  }

  function text(value) {
    const node = document.createElement('div');
    node.textContent = value ?? '';
    return node.innerHTML;
  }
})();
