<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Alert, Button, Empty, Spin } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import {
  listInterviews,
  type InterviewRecord,
} from '@/features/interview/api/interview-flow-api'
import InterviewRecordCard from '@/features/interview/components/InterviewRecordCard.vue'
import { ROUTE_PATHS } from '@/router/paths'
import { toApiError } from '@/shared/api/errors'

const router = useRouter()
const items = ref<InterviewRecord[]>([])
const cursor = ref<string | null>(null)
const loading = ref(false)
const errorMessage = ref('')

onMounted(() => load())

async function load() {
  if (loading.value) return
  const requestCursor = cursor.value ?? undefined
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await listInterviews(requestCursor)
    items.value.push(...page.items)
    cursor.value = page.nextCursor
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    loading.value = false
  }
}

function open(item: InterviewRecord) {
  if (item.stage === 'PREPARING' || item.stage === 'READY') {
    void router.push(`/app/interviews/${item.interviewId}/ready`)
    return
  }
  if (item.stage === 'INTERVIEWING') {
    void router.push(`/app/interviews/${item.interviewId}/session`)
    return
  }
  void router.push(`/app/interviews/${item.interviewId}/report`)
}
</script>

<template>
  <section class="records-page">
    <header class="records-page__header">
      <div>
        <p class="records-page__eyebrow">面试记录</p>
        <h2>每一场面试，都可以回来继续。</h2>
        <p>恢复未完成的面试，或查看已经生成的报告。</p>
      </div>
      <Button type="primary" size="large" @click="router.push(ROUTE_PATHS.interviews)">
        开始新面试
      </Button>
    </header>

    <Alert
      v-if="errorMessage"
      type="error"
      show-icon
      :message="errorMessage"
      description="已加载的记录不会丢失，可以重试当前页。"
    >
      <template #action>
        <Button size="small" :loading="loading" @click="load">重试</Button>
      </template>
    </Alert>

    <Spin :spinning="loading && items.length === 0">
      <div v-if="items.length" class="records-list" aria-label="面试记录列表">
        <div class="records-list__head" aria-hidden="true">
          <span>模式</span>
          <span>创建时间</span>
          <span>状态</span>
          <span>作答</span>
          <span>操作</span>
        </div>
        <InterviewRecordCard
          v-for="item in items"
          :key="item.interviewId"
          :record="item"
          @open="open"
        />
      </div>

      <div v-else-if="!loading && !errorMessage" class="records-page__empty">
        <Empty description="还没有面试记录" />
        <Button type="primary" @click="router.push(ROUTE_PATHS.interviews)">开始第一场面试</Button>
      </div>
    </Spin>

    <div v-if="cursor && !errorMessage" class="records-page__more">
      <Button
        data-testid="load-more-records"
        size="large"
        :loading="loading"
        @click="load"
      >
        加载更多
      </Button>
    </div>
  </section>
</template>

<style scoped>
.records-page {
  display: grid;
  gap: 30px;
}

.records-page__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding: 18px 0 12px;
}

.records-page__eyebrow {
  margin: 0 0 10px;
  color: var(--ssm-color-brand);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.06em;
}

.records-page h2 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: clamp(28px, 4vw, 42px);
  letter-spacing: -0.035em;
}

.records-page__header p:last-child {
  margin: 14px 0 0;
  color: var(--ssm-color-text-secondary);
}

.records-list {
  overflow: hidden;
  border: 1px solid var(--ssm-color-line);
  border-bottom: 0;
  border-radius: var(--ssm-radius-card);
}

.records-list__head {
  display: grid;
  grid-template-columns: minmax(120px, 0.8fr) minmax(190px, 1.1fr) minmax(130px, 0.8fr) minmax(140px, 0.9fr) auto;
  gap: 20px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--ssm-color-line);
  background: var(--ssm-color-paper);
  color: var(--ssm-color-text-secondary);
  font-size: 12px;
  font-weight: 650;
}

.records-list__head span:last-child {
  justify-self: end;
}

.records-page__empty {
  display: grid;
  justify-items: center;
  padding: 72px 20px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-card);
  background: var(--ssm-color-surface);
}

.records-page__more {
  display: flex;
  justify-content: center;
}

@media (max-width: 900px) {
  .records-list__head {
    display: none;
  }
}

@media (max-width: 680px) {
  .records-page__header {
    align-items: stretch;
    flex-direction: column;
  }

  .records-page__header .ant-btn {
    align-self: flex-start;
  }
}
</style>
