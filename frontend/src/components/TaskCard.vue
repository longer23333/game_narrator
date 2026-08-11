<script setup>
import {currentStageText,formatDate,stageTitle} from '../task-presenter.js';
defineProps({task:{type:Object,required:true}});
</script>
<template>
  <div class="task-card" :data-task-id="task.id" role="button" tabindex="0" :aria-label="`查看任务 ${task.name} 的详情`">
    <div class="task-head"><strong>{{ task.name }}</strong><div class="task-card-actions"><span>{{ task.status }}</span><button type="button" class="task-card-rename" :data-rename-list-task="task.id" :data-task-name="task.name">重命名</button><button type="button" class="task-card-delete" :data-delete-list-task="task.id" :data-task-name="task.name" :aria-label="`删除任务 ${task.name}`">删除</button></div></div>
    <div class="tags"><i>{{ task.gameCategory }}</i><i>{{ task.editingScope==='HIGHLIGHTS'?'精彩片段':'完整视频' }}</i><i>{{ formatDate(task.createdAt) }}</i></div>
    <div v-if="task.failureReason" class="task-error">{{ task.failureReason }}</div>
    <div class="stage-line"><span v-for="stage in task.stages" :key="stage.type" :class="stage.status.toLowerCase()" :title="stageTitle(stage)"></span></div>
    <div class="stage-caption">{{ currentStageText(task) }}</div>
  </div>
</template>
