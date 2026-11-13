// @vitest-environment jsdom
import {createPinia, setActivePinia} from 'pinia';
import {afterEach, expect, it, vi} from 'vitest';
import {useTaskStore} from '../stores/tasks.js';

afterEach(()=>vi.unstubAllGlobals());

it('clears a failed load when the stream supplies a recovered snapshot', async () => {
  setActivePinia(createPinia());
  vi.stubGlobal('fetch', vi.fn(async()=>({ok:false,status:500,json:async()=>({})})));
  let connection;
  vi.stubGlobal('EventSource', class { constructor() { connection = this; } });
  const store = useTaskStore();
  await expect(store.refresh()).rejects.toThrow('500');
  expect(store.error).toContain('500');
  store.startStream();
  connection.onmessage({data:JSON.stringify({type:'snapshot',tasks:[]})});
  expect(store.error).toBe('');
  expect(store.tasks).toEqual([]);
});
