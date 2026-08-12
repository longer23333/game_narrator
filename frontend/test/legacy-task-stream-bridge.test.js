import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const appSource = readFileSync(new URL('../public/app.js', import.meta.url), 'utf8');
const entrySource = readFileSync(new URL('../src/main.js', import.meta.url), 'utf8');

test('遗留分镜轮询只通过 Pinia 任务桥读取 SSE 连接状态', () => {
  assert.doesNotMatch(appSource, /\btaskStreamConnected\b/);
  assert.match(appSource, /gameNarratorTasks\?\.isStreamConnected\(\)/);
  assert.match(entrySource, /isStreamConnected:\(\)=>tasks\.streamConnected/);
});
