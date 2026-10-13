<script setup lang="ts">
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import { Spin } from 'ant-design-vue'

import type { InterviewMode } from '@/features/interview/api/interview-flow-api'

defineProps<{ mode: InterviewMode }>()
</script>

<template>
  <section class="waiting-state" aria-live="polite">
    <p class="waiting-state__accepted">
      <CheckCircleOutlined aria-hidden="true" />
      当前题已经处理，已提交内容保持冻结
    </p>
    <Spin size="large" />
    <div>
      <h1>正在整理这次回答并准备下一题。</h1>
      <p>
        {{ mode === 'COMPREHENSIVE' ? '等待期间不计入本场有效作答时间。' : '等待期间不计入 60 分钟有效作答时间。' }}
        你可以暂时离开，回来后仍从当前进度继续。
      </p>
    </div>
  </section>
</template>

<style scoped>
.waiting-state { display: grid; min-height: calc(100vh - 150px); align-content: center; justify-items: center; gap: 26px; padding: 52px 20px; text-align: center; }
.waiting-state__accepted { display: inline-flex; align-items: center; gap: 8px; margin: 0; color: var(--ssm-color-success); font-size: 14px; font-weight: 650; }
.waiting-state div { max-width: 660px; }
.waiting-state h1 { margin: 0; color: var(--ssm-color-ink); font-size: clamp(28px, 4vw, 42px); letter-spacing: -0.035em; }
.waiting-state div p { margin: 16px 0 0; color: var(--ssm-color-text-secondary); line-height: 1.8; }
</style>
