import {describe,expect,it,vi} from 'vitest';
import {applyTaskStreamPayload,createTaskStream} from './task-stream.js';
describe('task stream',()=>{
  it('applies snapshot and delta incrementally in newest-first order',()=>{const snapshot=new Map();applyTaskStreamPayload(snapshot,{type:'snapshot',tasks:[{id:'a',createdAt:'2026-01-01'}]});const tasks=applyTaskStreamPayload(snapshot,{type:'delta',tasks:[{id:'b',createdAt:'2026-02-01'}],removedIds:['a']});expect(tasks.map(task=>task.id)).toEqual(['b']);});
  it('backs off and reconnects after an error',()=>{const instances=[];class FakeEventSource{constructor(url){this.url=url;instances.push(this);}close=vi.fn();}const schedule=vi.fn();const stream=createTaskStream({EventSourceClass:FakeEventSource,onTasks:vi.fn(),onConnectionChange:vi.fn(),schedule});stream.connect();instances[0].onerror();expect(instances[0].close).toHaveBeenCalled();expect(schedule).toHaveBeenCalledWith(expect.any(Function),1000);});
});
