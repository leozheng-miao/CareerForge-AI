<script setup lang="ts">
import type { InterviewDifficulty } from '@/features/interview/api/interview-flow-api'
defineProps<{ durationSeconds: 3600 | 5400; difficulty: InterviewDifficulty }>()
const emit = defineEmits<{ 'update:durationSeconds': [value: 3600 | 5400]; 'update:difficulty': [value: InterviewDifficulty] }>()
const difficulties: InterviewDifficulty[] = ['BASIC', 'STANDARD', 'CHALLENGE']
const difficultyLabels: Record<InterviewDifficulty, string> = { BASIC: '基础', STANDARD: '标准', CHALLENGE: '挑战' }
</script>
<template><div class="settings"><fieldset><legend>有效作答时长</legend><label><input type="radio" :checked="durationSeconds===3600" @change="emit('update:durationSeconds',3600)">60 分钟</label><label><input type="radio" :checked="durationSeconds===5400" @change="emit('update:durationSeconds',5400)">90 分钟</label></fieldset><fieldset><legend>难度</legend><label v-for="item in difficulties" :key="item"><input type="radio" :checked="difficulty===item" @change="emit('update:difficulty',item)">{{ difficultyLabels[item] }}</label></fieldset></div></template>
<style scoped>.settings{display:grid;grid-template-columns:1fr 1fr;gap:20px}.settings fieldset{padding:16px;border:1px solid var(--cf-color-border);border-radius:12px}.settings label{display:block;margin:10px 0}.settings input{margin-right:7px}</style>
