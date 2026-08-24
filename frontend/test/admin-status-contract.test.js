import test from 'node:test';
import assert from 'node:assert/strict';
import status from '../public/admin-status.js';

test('后台状态徽章覆盖项目完成态与失败态',()=>{
  assert.equal(status.badgeClass('READY'),'ok');
  assert.equal(status.badgeClass('COMPLETED'),'ok');
  assert.equal(status.badgeClass('FAILED'),'bad');
  assert.equal(status.badgeClass('PERMANENT_FAILURE'),'bad');
});

test('后台筛选值与可重试状态使用统一契约',()=>{
  assert.deepEqual(status.filterValues('projects'),['DRAFT','PROCESSING','READY','FAILED','ARCHIVED']);
  assert.deepEqual(status.filterValues('cloud-sync'),['LOCAL_ONLY','PENDING','SYNCING','SYNCED','FAILED','PERMANENT_FAILURE']);
  assert.equal(status.canRetry('LOCAL_ONLY'),true);
  assert.equal(status.canRetry('FAILED'),true);
  assert.equal(status.canRetry('PERMANENT_FAILURE'),true);
  assert.equal(status.canRetry('SYNCED'),false);
});
