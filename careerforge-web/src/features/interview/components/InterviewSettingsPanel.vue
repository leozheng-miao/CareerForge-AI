<script setup lang="ts">
import type { InterviewDifficulty } from '@/features/interview/api/interview-flow-api'

defineProps<{ durationSeconds: 3600 | 5400; difficulty: InterviewDifficulty }>()
const emit = defineEmits<{
  'update:durationSeconds': [value: 3600 | 5400]
  'update:difficulty': [value: InterviewDifficulty]
}>()
const durations: Array<{ value: 3600 | 5400; label: string; description: string }> = [
  { value: 3600, label: '60 分钟', description: '常规节奏，全场最多 4 次追问' },
  { value: 5400, label: '90 分钟', description: '更充分的作答时间，全场最多 8 次追问' },
]
const difficulties: Array<{
  value: InterviewDifficulty
  label: string
  description: string
}> = [
  { value: 'BASIC', label: '基础', description: '重点检查核心概念和基本实践' },
  { value: 'STANDARD', label: '标准', description: '兼顾原理、工程取舍和实际经验' },
  { value: 'CHALLENGE', label: '挑战', description: '更深入地追问边界、失败和可靠性' },
]
</script>

<template>
  <div class="settings-panel">
    <fieldset>
      <legend>有效作答时长</legend>
      <p>等待下一题和报告生成时间不计入有效作答时长。</p>
      <div class="settings-panel__choices settings-panel__choices--two">
        <label
          v-for="item in durations"
          :key="item.value"
          class="setting-choice"
          :class="{ 'setting-choice--selected': durationSeconds === item.value }"
        >
          <input
            type="radio"
            name="durationSeconds"
            :value="item.value"
            :checked="durationSeconds === item.value"
            @change="emit('update:durationSeconds', item.value)"
          >
          <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        </label>
      </div>
    </fieldset>

    <fieldset>
      <legend>面试难度</legend>
      <p>难度只影响问题深度，不会被用作对你能力的预先结论。</p>
      <div class="settings-panel__choices settings-panel__choices--three">
        <label
          v-for="item in difficulties"
          :key="item.value"
          class="setting-choice"
          :class="{ 'setting-choice--selected': difficulty === item.value }"
        >
          <input
            type="radio"
            name="difficulty"
            :value="item.value"
            :checked="difficulty === item.value"
            @change="emit('update:difficulty', item.value)"
          >
          <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        </label>
      </div>
    </fieldset>
  </div>
</template>

<style scoped>
.settings-panel {
  display: grid;
  gap: 30px;
}

.settings-panel fieldset {
  min-width: 0;
  padding: 0;
  border: 0;
}

.settings-panel legend {
  padding: 0;
  color: var(--ssm-color-ink);
  font-size: 18px;
  font-weight: 700;
}

.settings-panel fieldset > p {
  margin: 8px 0 16px;
  color: var(--ssm-color-text-secondary);
  line-height: 1.6;
}

.settings-panel__choices {
  display: grid;
  gap: 12px;
}

.settings-panel__choices--two {
  grid-template-columns: repeat(2, 1fr);
}

.settings-panel__choices--three {
  grid-template-columns: repeat(3, 1fr);
}

.setting-choice {
  display: flex;
  min-height: 112px;
  align-items: flex-start;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-control);
  background: var(--ssm-color-surface);
  cursor: pointer;
  transition:
    border-color var(--ssm-motion-fast) ease,
    background var(--ssm-motion-fast) ease;
}

.setting-choice:hover,
.setting-choice--selected {
  border-color: var(--ssm-color-brand);
  background: var(--ssm-color-brand-soft);
}

.setting-choice input {
  margin-top: 3px;
  accent-color: var(--ssm-color-brand);
}

.setting-choice span {
  display: grid;
  gap: 7px;
}

.setting-choice strong {
  color: var(--ssm-color-ink);
  font-size: 16px;
}

.setting-choice small {
  color: var(--ssm-color-text-secondary);
  line-height: 1.55;
}

@media (max-width: 720px) {
  .settings-panel__choices--two,
  .settings-panel__choices--three {
    grid-template-columns: 1fr;
  }

  .setting-choice {
    min-height: auto;
  }
}
</style>
