import {reactive} from 'vue';
import {defineStore} from 'pinia';

const legacyKeys = {
  'gameNarrator.publicAssetSearch':'assetSearch',
  'gameNarrator.mediaAuthenticationPreferences':'mediaAuthentication',
  'gameNarrator.mediaImportPreferences':'mediaImport',
  'gameNarrator.bilibiliRecommendationSyncAt':'bilibiliRecommendationSync'
};

export const usePreferenceStore = defineStore('preferences', () => {
  const values = reactive({});
  const taskDraft = reactive({gameCategory:'', editingScope:'FULL_VIDEO', targetDurationSeconds:90});
  const get = (key, fallback = null) => values[key] ?? fallback;
  const set = (key, value) => { values[key] = value; };
  const remove = key => { delete values[key]; };
  function migrateLegacy(storage = window.localStorage) {
    Object.entries(legacyKeys).forEach(([oldKey, newKey]) => {
      if (values[newKey] !== undefined) return;
      const raw = storage.getItem(oldKey); if (raw == null) return;
      try { values[newKey] = JSON.parse(raw); } catch { values[newKey] = raw; }
      storage.removeItem(oldKey);
    });
  }
  return {values, taskDraft, get, set, remove, migrateLegacy};
}, {persist:{key:'gameNarrator.frontendState'}});
