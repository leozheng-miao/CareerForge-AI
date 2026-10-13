<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRightOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'

import type {
  InterviewRecord,
  InterviewStage,
} from '@/features/interview/api/interview-flow-api'

const props = defineProps<{ record: InterviewRecord }>()
defineEmits<{ open: [record: InterviewRecord] }>()

const stageDetails: Record<InterviewStage, { action: string; label: string; tone: string }> = {
  PREPARING: { action: '查看准备进度', label: '准备中', tone: 'waiting' },
  READY: { action: '开始面试', label: '待开始', tone: 'ready' },
  INTERVIEWING: { action: '继续面试', label: '进行中', tone: 'active' },
  GENERATING_REPORT: { action: '查看报告进度', label: '报告生成中', tone: 'waiting' },
  COMPLETED: { action: '查看报告', label: '已完成', tone: 'completed' },
  FAILED: { action: '查看详情', label: '未完成', tone: 'failed' },
}

const stage = computed(() => stageDetails[props.record.stage])
const createdAt = computed(() => new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
}).format(new Date(props.record.createdAt)))
</script>

<template>
  <article class="record-row">
    <strong class="record-row__mode">
      {{ record.mode === 'COMPREHENSIVE' ? '综合面试' : '快速面试' }}
    </strong>
    <time class="record-row__time" :datetime="record.createdAt">{{ createdAt }}</time>
    <span class="record-row__status" :class="`record-row__status--${stage.tone}`">
      {{ stage.label }}
    </span>
    <span class="record-row__answers">已提交 {{ record.submittedAnswerCount }} 个回答</span>
    <Button class="record-row__action" type="link" @click="$emit('open', record)">
      {{ stage.action }}
      <ArrowRightOutlined aria-hidden="true" />
    </Button>
  </article>
</template>

<style scoped>
.record-row {
  display: grid;
  min-height: 76px;
  grid-template-columns: minmax(120px, 0.8fr) minmax(190px, 1.1fr) minmax(130px, 0.8fr) minmax(140px, 0.9fr) auto;
  align-items: center;
  gap: 20px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--ssm-color-line);
  background: var(--ssm-color-surface);
}

.record-row__mode {
  color: var(--ssm-color-ink);
  font-size: 15px;
}

.record-row__time,
.record-row__answers {
  color: var(--ssm-color-text-secondary);
  font-size: 14px;
}

.record-row__status {
  width: fit-content;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--ssm-color-brand-soft);
  color: var(--ssm-color-brand);
  font-size: 13px;
  font-weight: 650;
}

.record-row__status--waiting {
  background: #f5ebdd;
  color: var(--ssm-color-warning);
}

.record-row__status--completed {
  background: #e4f1e9;
  color: var(--ssm-color-success);
}

.record-row__status--failed {
  background: #f7e7e5;
  color: var(--ssm-color-error);
}

.record-row__action {
  justify-self: end;
  padding-inline: 4px;
  font-weight: 650;
}

@media (max-width: 900px) {
  .record-row {
    grid-template-columns: 1fr auto;
    gap: 8px 20px;
    padding-block: 18px;
  }

  .record-row__time,
  .record-row__answers {
    grid-column: 1;
  }

  .record-row__status {
    grid-column: 2;
    grid-row: 1;
  }

  .record-row__action {
    grid-column: 2;
    grid-row: 2 / span 2;
    align-self: end;
  }
}

@media (max-width: 560px) {
  .record-row {
    grid-template-columns: 1fr;
  }

  .record-row__time,
  .record-row__answers,
  .record-row__status,
  .record-row__action {
    grid-column: 1;
    grid-row: auto;
    justify-self: start;
  }
}
</style>
