import {computed, ref} from 'vue';
import {defineStore} from 'pinia';
import {applyTaskStreamPayload, createTaskStream} from '../services/task-stream.js';

async function apiError(response) {
  const body = await response.json().catch(() => ({}));
  return new Error(body.message || `请求失败（HTTP ${response.status}）`);
}

export const useTaskStore = defineStore('tasks', () => {
  const tasks = ref([]);
  const loading = ref(false);
  const error = ref('');
  const streamConnected = ref(false);
  const snapshot = new Map();
  let stream;
  const activeTasks = computed(() => tasks.value.filter(task => task.status !== 'COMPLETED'));
  const recentTasks = computed(() => tasks.value.filter(task => task.status === 'COMPLETED').slice(0, 8));
  function publish(nextTasks) {
    tasks.value = nextTasks;
    window.dispatchEvent(new CustomEvent('gamenarrator:tasks', {detail:nextTasks}));
  }
  async function refresh() {
    if (loading.value) return tasks.value;
    loading.value = true; error.value = '';
    try {
      const response = await fetch('/api/tasks');
      if (!response.ok) throw await apiError(response);
      const nextTasks = await response.json();
      snapshot.clear(); nextTasks.forEach(task => snapshot.set(task.id, task)); publish(nextTasks);
      return nextTasks;
    } catch (cause) { error.value = cause.message; throw cause; }
    finally { loading.value = false; }
  }
  function startStream() {
    if (stream) return;
    stream = createTaskStream({EventSourceClass:window.EventSource,
      onConnectionChange:connected => { streamConnected.value = connected; },
      onTasks:payload => { try { const next = applyTaskStreamPayload(snapshot, payload); if (next) publish(next); }
        catch (cause) { console.warn('[GameNarrator] SSE payload ignored', cause); } }});
    stream.connect();
  }
  return {tasks, loading, error, streamConnected, activeTasks, recentTasks, refresh, startStream};
});
