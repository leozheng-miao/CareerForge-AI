<script setup lang="ts">
import { computed } from 'vue'
import { Button } from 'ant-design-vue'

import type { InterviewMode } from '@/features/interview/api/interview-flow-api'

const props = withDefaults(defineProps<{
  remainingSeconds: number
  paused: boolean
  progress: number
  mode: InterviewMode
  waiting?: boolean
  timeExhausted?: boolean
}>(), { waiting: false, timeExhausted: false })
defineEmits<{ pause: []; finish: [] }>()

const time = computed(() => `${String(Math.floor(props.remainingSeconds / 60)).padStart(2, '0')}:${String(props.remainingSeconds % 60).padStart(2, '0')}`)
const comprehensive = computed(() => props.mode === 'COMPREHENSIVE')
const pauseLabel = computed(() => {
  if (props.waiting) return '等待下一题'
  return props.paused ? '继续面试' : '暂停'
})
</script>

<template>
  <header class="interview-topbar">
    <div class="interview-topbar__context">
      <strong>{{ comprehensive ? '综合面试' : '快速面试' }}</strong>
      <span>{{ progress }} / {{ comprehensive ? 6 : 8 }} {{ comprehensive ? '个主题' : '个考查目标' }}</span>
    </div>
    <div class="interview-topbar__actions">
      <div class="interview-topbar__timer" aria-live="polite">
        <small>剩余有效时间</small>
        <strong>{{ time }}</strong>
      </div>
      <span v-if="waiting" class="interview-topbar__state">等待中</span>
      <span v-else-if="timeExhausted" class="interview-topbar__state">本题完成后结束</span>
      <span v-else-if="paused" class="interview-topbar__state">已暂停</span>
      <Button v-if="!timeExhausted" :disabled="waiting" @click="$emit('pause')">{{ pauseLabel }}</Button>
      <Button danger @click="$emit('finish')">结束面试</Button>
    </div>
  </header>
</template>

<style scoped>
.interview-topbar { position: sticky; z-index: 20; top: 0; display: flex; min-height: 78px; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 0; border-bottom: 1px solid var(--ssm-color-line); background: color-mix(in srgb, var(--ssm-color-paper) 94%, transparent); backdrop-filter: blur(12px); }
.interview-topbar__context, .interview-topbar__actions { display: flex; align-items: center; gap: 14px; }
.interview-topbar__context strong { color: var(--ssm-color-ink); font-size: 16px; }
.interview-topbar__context span, .interview-topbar__state { color: var(--ssm-color-text-secondary); font-size: 13px; }
.interview-topbar__timer { display: grid; justify-items: end; }
.interview-topbar__timer small { color: var(--ssm-color-text-secondary); font-size: 11px; }
.interview-topbar__timer strong { color: var(--ssm-color-ink); font-size: 24px; font-variant-numeric: tabular-nums; letter-spacing: -0.02em; }
@media (max-width: 720px) { .interview-topbar { position: static; align-items: flex-start; flex-direction: column; } .interview-topbar__actions { width: 100%; flex-wrap: wrap; } .interview-topbar__timer { margin-right: auto; justify-items: start; } }
</style>
