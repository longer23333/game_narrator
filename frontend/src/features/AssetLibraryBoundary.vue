<script setup>
import {onMounted,ref} from 'vue'; const failed=ref('');
import {loadLegacyScript} from '../services/legacy-script.js';
onMounted(async()=>{try{await loadLegacyScript('/asset-library.js',{type:'module'});}catch(error){failed.value=error.message;}});
</script>
<template><p v-if="failed" class="empty">素材库模块加载失败：{{ failed }}</p></template>
