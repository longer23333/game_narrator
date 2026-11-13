// @vitest-environment jsdom
import {readFileSync} from 'node:fs';
import {expect, it, vi} from 'vitest';

// Exercise the legacy editor's real entry points without booting the whole app.
const source = readFileSync('public/app.js', 'utf8');
const editorCode = source.slice(source.indexOf('async function loadStoryboardEditor('), source.indexOf('function renderRevisionTree('));
function editor() {
  document.body.innerHTML = '<dialog open><div id="workspace"></div></dialog>';
  const workspace = document.querySelector('#workspace');
  workspace.scrollTo = vi.fn();
  window.gameNarratorTasks = {isStreamConnected: () => true};
  const requestJson = vi.fn(async url => {
    if (url.endsWith('/storyboard')) return {segments:[{clipIndex:1,startSeconds:0,endSeconds:5,narration:'原文',subtitle:'字幕'}]};
    if (url.endsWith('/events')) throw new Error('事件服务离线');
    return [];
  });
  const dependencies = {
    requestJson, storyboardWorkspace:workspace, storyboardDialog:document.querySelector('dialog'),
    captureStoryboardViewState:()=>null, restoreStoryboardViewState:vi.fn(),
    mountStoryboardTimeline:vi.fn(), mountRevisionTree:vi.fn(), updateStoryboardProgress:vi.fn(),
    escapeHtml:value=>String(value ?? ''), formatDuration:String, renderRevisionTree:()=>'',
    renderGameEventTimeline:()=>'<p>事件分析</p>', renderDirectorReviewBoard:()=>'<p>评审</p>',
    renderDirectorIntelligence:()=>'', renderCommunityEcosystem:()=>''
  };
  const api = new Function(...Object.keys(dependencies), `let storyboardProgressTimer; ${editorCode}; return {loadStoryboardEditor,loadAdvancedPlanning};`)(...Object.values(dependencies));
  return {...api,workspace,requestJson};
}

it('opens basic editing without requesting optional planning services', async () => {
  const app = editor();
  await app.loadStoryboardEditor('test-task');
  expect(app.requestJson).toHaveBeenCalledTimes(6);
  expect(app.requestJson.mock.calls.some(([url])=>url.includes('/events') || url.includes('director') || url.includes('community'))).toBe(false);
  expect(app.workspace.querySelector('[name="narration"]').value).toBe('原文');
  expect(app.workspace.querySelector('[data-advanced-planning]').open).toBe(false);
});

it('keeps edits usable when advanced loading fails and allows retry', async () => {
  const app = editor();
  await app.loadStoryboardEditor('test-task');
  const text = app.workspace.querySelector('[name="narration"]');
  text.value = '尚未保存的修改';
  const panel = app.workspace.querySelector('[data-advanced-planning]');
  await app.loadAdvancedPlanning('test-task', panel);
  expect(panel.textContent).toContain('事件服务离线');
  expect(text.value).toBe('尚未保存的修改');
  app.requestJson.mockImplementation(async()=>[]);
  await app.loadAdvancedPlanning('test-task', panel);
  expect(panel.dataset.loaded).toBe('true');
  expect(panel.textContent).toContain('事件分析');
  expect(text.value).toBe('尚未保存的修改');
  const count = app.requestJson.mock.calls.length;
  await app.loadAdvancedPlanning('test-task', panel);
  expect(app.requestJson).toHaveBeenCalledTimes(count);
});
