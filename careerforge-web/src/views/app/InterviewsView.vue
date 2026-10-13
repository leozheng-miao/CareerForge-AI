<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRightOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, message, Spin } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import {
  getInterview,
  getResumableInterview,
  listInterviews,
  type InterviewRecord,
  type InterviewSession,
} from '@/features/interview/api/interview-flow-api'
import InterviewRecordCard from '@/features/interview/components/InterviewRecordCard.vue'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { ROUTE_PATHS } from '@/router/paths'
import { toApiError } from '@/shared/api/errors'

type ResumableSummary = NonNullable<Awaited<ReturnType<typeof getResumableInterview>>>
type ActiveInterview = { session: InterviewSession; summary: ResumableSummary }
type ActiveCheckState = 'loading' | 'ready' | 'error'

const router = useRouter()
const store = useInterviewFlowStore()
const activeInterview = ref<ActiveInterview | null>(null)
const activeCheckState = ref<ActiveCheckState>('loading')
const activeCheckError = ref('')
const recentCandidates = ref<InterviewRecord[]>([])
const recentLoading = ref(true)
const recentError = ref('')
const creating = ref(false)

const creationDisabled = computed(
  () => activeCheckState.value !== 'ready' || Boolean(activeInterview.value),
)
const recentRecords = computed(() => recentCandidates.value
  .filter((item) => item.interviewId !== activeInterview.value?.session.interviewId)
  .slice(0, 3))

onMounted(() => {
  void Promise.all([loadActiveInterview(), loadRecentInterviews()])
})

async function loadActiveInterview() {
  activeCheckState.value = 'loading'
  activeCheckError.value = ''
  try {
    const summary = await getResumableInterview()
    if (!summary) {
      activeInterview.value = null
      activeCheckState.value = 'ready'
      return
    }
    const session = await getInterview(summary.interviewId)
    activeInterview.value = { session, summary }
    activeCheckState.value = 'ready'
  } catch (error) {
    activeInterview.value = null
    activeCheckState.value = 'error'
    activeCheckError.value = toApiError(error).message
  }
}

async function loadRecentInterviews() {
  recentLoading.value = true
  recentError.value = ''
  try {
    const page = await listInterviews(undefined, 4)
    recentCandidates.value = page.items
  } catch (error) {
    recentError.value = toApiError(error).message
  } finally {
    recentLoading.value = false
  }
}

function canCreateInterview() {
  if (activeInterview.value) {
    message.warning('你还有一场未完成的面试')
    return false
  }
  if (activeCheckState.value !== 'ready') {
    message.warning('请先重试未完成面试检查')
    return false
  }
  return true
}

async function startQuickInterview() {
  if (!canCreateInterview() || creating.value) return
  creating.value = true
  try {
    const value = await store.createQuick()
    await router.push(`/app/interviews/${value.interviewId}/ready`)
  } catch (error) {
    message.error(toApiError(error).message)
  } finally {
    creating.value = false
  }
}

function startComprehensiveInterview() {
  if (!canCreateInterview()) return
  void router.push(ROUTE_PATHS.comprehensiveInterviewSetup)
}

function openInterview(record: InterviewRecord | InterviewSession) {
  if (record.stage === 'PREPARING' || record.stage === 'READY') {
    void router.push(`/app/interviews/${record.interviewId}/ready`)
    return
  }
  if (record.stage === 'INTERVIEWING') {
    void router.push(`/app/interviews/${record.interviewId}/session`)
    return
  }
  void router.push(`/app/interviews/${record.interviewId}/report`)
}

function activeActionLabel(stage: InterviewSession['stage']) {
  if (stage === 'PREPARING') return '查看准备进度'
  if (stage === 'READY') return '开始面试'
  if (stage === 'INTERVIEWING') return '继续面试'
  return '查看当前状态'
}

function formatRemaining(seconds: number) {
  return `${Math.max(0, Math.ceil(seconds / 60))} 分钟`
}
</script>

<template>
  <section class="interview-center">
    <header class="interview-center__header">
      <p>水水面 · 面试中心</p>
      <h2>准备开始哪一种面试？</h2>
      <span>当前仅支持文字作答。回答提交后会保存并冻结。</span>
    </header>

    <Spin v-if="activeCheckState === 'loading'" class="active-interview-loading" tip="正在检查未完成面试…" />

    <Alert
      v-else-if="activeCheckState === 'error'"
      class="active-interview-error"
      type="warning"
      show-icon
      message="暂时无法确认是否有未完成面试"
      :description="`${activeCheckError}。为避免同时创建两场面试，当前已暂停新建。`"
    >
      <template #action>
        <Button @click="loadActiveInterview">
          <ReloadOutlined />
          重试检查
        </Button>
      </template>
    </Alert>

    <section v-else-if="activeInterview" class="active-interview" aria-labelledby="active-title">
      <div>
        <p>未完成面试</p>
        <h3 id="active-title">
          {{ activeInterview.session.mode === 'COMPREHENSIVE' ? '综合面试' : '快速面试' }}
        </h3>
        <span v-if="activeInterview.session.stage === 'INTERVIEWING'">
          继续第 {{ activeInterview.summary.currentQuestionNumber }} 个问题
          · 剩余约 {{ formatRemaining(activeInterview.summary.remainingSeconds) }}
        </span>
        <span v-else-if="activeInterview.session.stage === 'PREPARING'">平台正在准备这场面试。</span>
        <span v-else>这场面试已经准备好，等待你确认开始。</span>
      </div>
      <Button type="primary" size="large" @click="openInterview(activeInterview.session)">
        {{ activeActionLabel(activeInterview.session.stage) }}
        <ArrowRightOutlined aria-hidden="true" />
      </Button>
    </section>

    <section class="interview-modes" aria-labelledby="mode-title">
      <header class="interview-section-heading">
        <p>面试模式</p>
        <h3 id="mode-title">选择这次要练习的方式。</h3>
      </header>
      <div class="interview-modes__grid">
        <article class="interview-mode-entry">
          <div>
            <p>快速面试</p>
            <h4>60 分钟 · 8 个考查目标</h4>
            <span>无需上传材料，直接练习 AI Agent 开发的通用能力。</span>
          </div>
          <Button
            data-testid="start-quick-interview"
            type="primary"
            size="large"
            :loading="creating"
            :disabled="creationDisabled"
            @click="startQuickInterview"
          >
            开始快速面试
          </Button>
        </article>

        <article class="interview-mode-entry">
          <div>
            <p>综合面试</p>
            <h4>结合岗位与真实经历</h4>
            <span>先确认岗位和个人材料，再完成 6 个岗位相关主题。</span>
          </div>
          <Button
            size="large"
            :disabled="creationDisabled"
            @click="startComprehensiveInterview"
          >
            配置综合面试
          </Button>
        </article>
      </div>
      <p v-if="activeInterview" class="interview-modes__notice">
        完成或结束当前面试后，才能创建新的面试。
      </p>
    </section>

    <section class="recent-interviews" aria-labelledby="recent-title">
      <header class="recent-interviews__header">
        <div class="interview-section-heading">
          <p>最近面试</p>
          <h3 id="recent-title">继续未完成的记录，或回看正式报告。</h3>
        </div>
        <Button type="link" @click="router.push(ROUTE_PATHS.interviewRecords)">
          查看全部
          <ArrowRightOutlined aria-hidden="true" />
        </Button>
      </header>

      <Alert
        v-if="recentError"
        type="error"
        show-icon
        :message="recentError"
        description="最近面试暂未加载，不影响开始新面试。"
      >
        <template #action><Button size="small" @click="loadRecentInterviews">重试</Button></template>
      </Alert>
      <Spin v-else-if="recentLoading" tip="正在加载最近面试…" />
      <div v-else-if="recentRecords.length" class="recent-interviews__list">
        <InterviewRecordCard
          v-for="item in recentRecords"
          :key="item.interviewId"
          :record="item"
          @open="openInterview"
        />
      </div>
      <div v-else class="recent-interviews__empty">
        <ClockCircleOutlined aria-hidden="true" />
        <span>还没有面试记录。完成第一场后，这里会保留进度和报告。</span>
      </div>
    </section>
  </section>
</template>

<style scoped>
.interview-center {
  display: grid;
  gap: 48px;
}

.interview-center__header {
  padding: 18px 0 4px;
}

.interview-center__header > p,
.interview-section-heading > p,
.active-interview p,
.interview-mode-entry p {
  margin: 0 0 10px;
  color: var(--ssm-color-brand);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.06em;
}

.interview-center__header h2 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: clamp(32px, 4vw, 48px);
  letter-spacing: -0.04em;
}

.interview-center__header > span {
  display: block;
  margin-top: 16px;
  color: var(--ssm-color-text-secondary);
  font-size: 16px;
}

.active-interview-loading {
  justify-self: start;
  min-height: 70px;
}

.active-interview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 24px 28px;
  border: 1px solid var(--ssm-color-brand);
  border-radius: var(--ssm-radius-card);
  background: var(--ssm-color-brand-soft);
}

.active-interview h3 {
  margin: 0 0 8px;
  color: var(--ssm-color-ink);
  font-size: 24px;
}

.active-interview span {
  color: var(--ssm-color-text-secondary);
}

.interview-section-heading h3 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: clamp(24px, 3vw, 32px);
  letter-spacing: -0.025em;
}

.interview-modes__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-top: 24px;
  border-top: 1px solid var(--ssm-color-line);
  border-bottom: 1px solid var(--ssm-color-line);
}

.interview-mode-entry {
  display: flex;
  min-height: 240px;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding: 30px 34px 30px 4px;
}

.interview-mode-entry:first-child {
  border-right: 1px solid var(--ssm-color-line);
}

.interview-mode-entry + .interview-mode-entry {
  padding-inline: 34px 4px;
}

.interview-mode-entry h4 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: 23px;
}

.interview-mode-entry span {
  display: block;
  max-width: 440px;
  margin-top: 14px;
  color: var(--ssm-color-text-secondary);
  line-height: 1.7;
}

.interview-mode-entry .ant-btn {
  flex: 0 0 auto;
}

.interview-modes__notice {
  margin: 14px 0 0;
  color: var(--ssm-color-warning);
  font-size: 14px;
}

.recent-interviews {
  display: grid;
  gap: 22px;
}

.recent-interviews__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.recent-interviews__list {
  overflow: hidden;
  border: 1px solid var(--ssm-color-line);
  border-bottom: 0;
  border-radius: var(--ssm-radius-card);
}

.recent-interviews__empty {
  display: flex;
  min-height: 110px;
  align-items: center;
  gap: 12px;
  padding: 24px;
  border-top: 1px solid var(--ssm-color-line);
  border-bottom: 1px solid var(--ssm-color-line);
  color: var(--ssm-color-text-secondary);
}

.recent-interviews__empty svg {
  color: var(--ssm-color-brand);
  font-size: 20px;
}

@media (max-width: 1050px) {
  .interview-mode-entry {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .interview-center {
    gap: 38px;
  }

  .active-interview,
  .recent-interviews__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .interview-modes__grid {
    grid-template-columns: 1fr;
  }

  .interview-mode-entry,
  .interview-mode-entry + .interview-mode-entry {
    min-height: auto;
    padding: 26px 4px;
  }

  .interview-mode-entry:first-child {
    border-right: 0;
    border-bottom: 1px solid var(--ssm-color-line);
  }
}
</style>
