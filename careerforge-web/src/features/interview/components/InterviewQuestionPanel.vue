<script setup lang="ts">
import type { CurrentQuestion } from '@/features/interview/api/interview-flow-api'

defineProps<{ question: CurrentQuestion }>()
</script>

<template>
  <section class="question-panel" aria-labelledby="current-question-title">
    <p>{{ question.type === 'FOLLOW_UP' ? '继续追问' : '当前问题' }}</p>
    <h1 id="current-question-title">{{ question.content }}</h1>
  </section>
</template>

<style scoped>
.question-panel { padding: 42px 0 32px; border-bottom: 1px solid var(--ssm-color-line); }
.question-panel p { margin: 0 0 14px; color: var(--ssm-color-brand); font-size: 13px; font-weight: 700; letter-spacing: 0.06em; }
.question-panel h1 { max-width: 860px; margin: 0; color: var(--ssm-color-ink); font-size: clamp(25px, 3.4vw, 38px); font-weight: 680; letter-spacing: -0.025em; line-height: 1.55; white-space: pre-wrap; }
</style>
