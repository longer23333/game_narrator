export function applyTaskStreamPayload(snapshot, payload) {
  if (Array.isArray(payload)) {
    snapshot.clear();
    payload.forEach(task => snapshot.set(task.id, task));
  } else if (payload?.type === 'snapshot') {
    snapshot.clear();
    (payload.tasks || []).forEach(task => snapshot.set(task.id, task));
  } else if (payload?.type === 'delta') {
    (payload.tasks || []).forEach(task => snapshot.set(task.id, task));
    (payload.removedIds || []).forEach(id => snapshot.delete(id));
  } else if (payload?.type === 'heartbeat') return null;
  else throw new Error('unknown task stream payload');
  return [...snapshot.values()].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
}

export function createTaskStream({EventSourceClass, onTasks, onConnectionChange, schedule = setTimeout}) {
  let source;
  let retryMs = 1000;
  let stopped = false;
  const connect = () => {
    if (stopped || !EventSourceClass || source) return;
    source = new EventSourceClass('/api/tasks/stream');
    source.onopen = () => { retryMs = 1000; onConnectionChange(true); };
    source.onmessage = event => onTasks(JSON.parse(event.data));
    source.onerror = () => {
      onConnectionChange(false); source?.close(); source = undefined;
      const delay = retryMs; retryMs = Math.min(30000, retryMs * 2); schedule(connect, delay);
    };
  };
  return {connect, close() { stopped = true; source?.close(); source = undefined; }};
}
