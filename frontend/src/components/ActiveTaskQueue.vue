<script setup>
import {storeToRefs} from 'pinia';
import {useTaskStore} from '../stores/tasks.js';
import {currentStageText,missingToolGuidance,renderingProgressText,stageTitle,voiceProgressText} from '../task-presenter.js';
const {activeTasks}=storeToRefs(useTaskStore());
const runningStage=task=>task.stages.find(stage=>stage.status==='RUNNING');
const progress=task=>runningStage(task)?.progress??(task.status==='WAITING_REVIEW'?100:0);
</script>
<template>
  <p v-if="!activeTasks.length" class="empty">当前没有正在进行的任务</p>
  <template v-else>
    <small>ACTIVE QUEUE · {{ activeTasks.length }}</small>
    <div v-for="task in activeTasks" :key="task.id" class="active-task-item">
      <button type="button" class="active-task-card" :data-open-active-task="task.id">
        <span><strong>{{ task.name }}</strong><i>{{ task.status }}</i></span>
        <span class="active-task-meta"><i>{{ task.gameCategory }}</i><i>{{ task.editingScope==='HIGHLIGHTS'?'精彩片段':'完整视频' }}</i><i>{{ task.stages.filter(stage=>stage.status==='COMPLETED').length }} / {{ task.stages.length }} 阶段</i></span>
        <b>{{ currentStageText(task) }}</b><span class="active-progress"><i :style="{width:`${Math.max(0,Math.min(100,progress(task)))}%`}"></i></span>
        <span class="active-stage-line"><i v-for="stage in task.stages" :key="stage.type" :class="stage.status.toLowerCase()" :title="stageTitle(stage)"></i></span>
        <em v-if="runningStage(task)?.type==='VOICE_GENERATION'">{{ voiceProgressText(task,runningStage(task)) }}</em>
        <em v-if="runningStage(task)?.type==='RENDERING'">{{ renderingProgressText(task,runningStage(task)) }}</em>
        <em v-if="missingToolGuidance(task)" class="tool-guidance">{{ missingToolGuidance(task) }}</em>
      </button>
      <button v-if="task.status==='PROCESSING'" type="button" class="task-cancel" :data-cancel-task="task.id">取消当前任务</button>
    </div>
  </template>
</template>
