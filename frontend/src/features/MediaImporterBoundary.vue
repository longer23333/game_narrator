<script setup>
import {onMounted,ref} from 'vue'; const failed=ref('');
import {loadLegacyScript} from '../services/legacy-script.js';
onMounted(async()=>{try{await loadLegacyScript('/media-importer.js');}catch(error){failed.value=error.message;}});
</script>
<template><p v-if="failed" class="empty">平台导入模块加载失败：{{ failed }}</p></template>
