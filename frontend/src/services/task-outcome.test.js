// @vitest-environment jsdom
import {readFileSync} from 'node:fs';
import {expect, it} from 'vitest';

const source = readFileSync('public/app.js', 'utf8');
const code = source.slice(source.indexOf('function taskPrimaryActions('), source.indexOf('function renderTaskDetails('));

it('adds playback on completion without replacing unsaved fields or restarting playback', () => {
  document.body.innerHTML = '<main><p data-task-next-step></p><span data-task-live-progress></span><div class="task-primary-actions"></div><div data-task-live-failure></div><details open><textarea>未保存的文案</textarea></details></main>';
  const root = document.querySelector('main');
  const update = new Function('detailContent','stageNames','renderedVideoSection','taskFailureHtml', `${code}; return updateTaskOutcome;`)(root,{},()=>'<section class="rendered-video"><video controls></video></section>', text=>text);
  const task = {id:'test',status:'PROCESSING',stages:[{status:'RUNNING',progress:30}]};
  const text = root.querySelector('textarea');
  update(task);
  expect(root.querySelector('video')).toBeNull();
  task.status = 'COMPLETED'; task.renderedVideoPath = 'output.mp4'; task.generatedScriptPath = 'script.json'; task.stages[0].progress=100;
  update(task);
  const video = root.querySelector('video');
  expect(video).not.toBeNull();
  expect(root.querySelector('[data-task-next-step]').textContent).toContain('视频已完成');
  expect(root.querySelector('[data-open-storyboard]')).not.toBeNull();
  update(task);
  expect(root.querySelector('video')).toBe(video);
  expect(root.querySelector('textarea')).toBe(text);
  expect(text.value).toBe('未保存的文案');
  expect(root.querySelector('details').open).toBe(true);
});
