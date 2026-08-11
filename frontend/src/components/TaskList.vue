<script setup>
import {storeToRefs} from 'pinia';
import {useTaskStore} from '../stores/tasks.js';
import TaskCard from './TaskCard.vue';
const store=useTaskStore(); const {recentTasks,loading,error}=storeToRefs(store);
</script>
<template>
  <p v-if="loading && !recentTasks.length" class="empty">正在读取任务…</p>
  <p v-else-if="error && !recentTasks.length" class="empty">任务加载失败：{{ error }}</p>
  <p v-else-if="!recentTasks.length" class="empty">还没有已完成的任务。</p>
  <TaskCard v-for="task in recentTasks" :key="task.id" :task="task" />
</template>
