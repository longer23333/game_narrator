import test from 'node:test';
import assert from 'node:assert/strict';
import {mergeTagViews} from '../public/asset-tag-state.js';

test('批量添加标签时保留原标签且不制造重复项', () => {
  const result = mergeTagViews([
    {name:'战斗', userAdded:false, sources:['AI']},
    {name:'搞笑', userAdded:true, sources:['USER']}
  ], [' 战斗 ', '高能', '高能'], []);

  assert.deepEqual(result.map(tag => tag.name), ['战斗', '搞笑', '高能']);
  assert.equal(result[0].userAdded, true);
  assert.deepEqual(result[0].sources, ['AI', 'USER']);
});

test('删除标签按名称忽略大小写并且不修改输入数组', () => {
  const original = [{name:'Boss', userAdded:true, sources:['USER']}, {name:'剧情', userAdded:false, sources:['AI']}];
  const result = mergeTagViews(original, [], [' boss ']);

  assert.deepEqual(result.map(tag => tag.name), ['剧情']);
  assert.equal(original.length, 2);
});
