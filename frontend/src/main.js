import {createApp} from 'vue';
import {createPinia} from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import TaskList from './components/TaskList.vue';
import ActiveTaskQueue from './components/ActiveTaskQueue.vue';
import {useTaskStore} from './stores/tasks.js';
import {usePreferenceStore} from './stores/preferences.js';
import './memphis-theme.css';
import './memphis-motion.js';

const pinia=createPinia(); pinia.use(piniaPluginPersistedstate);
const tasks=useTaskStore(pinia); window.gameNarratorTasks={
  refresh:()=>tasks.refresh(),
  isStreamConnected:()=>tasks.streamConnected
};
try {
  createApp(TaskList).use(pinia).mount('#task-list');
  createApp(ActiveTaskQueue).use(pinia).mount('#active-task');
} catch(error) {
  window.gameNarratorBootError=error?.message||String(error);
  console.error('[GameNarrator] Vue mount failed',error);
}
const preferences=usePreferenceStore(pinia); preferences.migrateLegacy();
window.gameNarratorPreferences={get:(key,fallback)=>preferences.get(key,fallback),set:(key,value)=>preferences.set(key,value),remove:key=>preferences.remove(key)};
const taskForm=document.querySelector('#task-form');
for(const name of ['gameCategory','editingScope','targetDurationSeconds']) {
  const field=taskForm?.elements[name]; if(!field)continue;
  if(preferences.taskDraft[name]!==undefined&&preferences.taskDraft[name]!=='')field.value=preferences.taskDraft[name];
  field.addEventListener('change',()=>{preferences.taskDraft[name]=field.type==='number'?Number(field.value):field.value;});
}
const appModuleUrl='/app.js';
await import(/* @vite-ignore */ appModuleUrl); await tasks.refresh().catch(()=>undefined); tasks.startStream();
