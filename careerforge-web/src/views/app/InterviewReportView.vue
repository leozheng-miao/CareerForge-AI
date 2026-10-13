<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Button, Spin } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getInterview,
  getInterviewHistory,
  getInterviewReport,
  retryInterviewReport,
  type InterviewMode,
  type InterviewReport,
} from '@/features/interview/api/interview-flow-api'
import InterviewReportSection from '@/features/interview/components/InterviewReportSection.vue'
import { toApiError } from '@/shared/api/errors'

interface Finding {
  title: string
  description: string
  quote?: string
}

interface TopicReview {
  sequenceNo: number
  conclusion: string
}

interface ReportBody {
  summary: string
  strengths: Finding[]
  improvements: Finding[]
  topicReviews: TopicReview[]
}

interface HistoryTurn {
  question: {
    questionId: string
    content: string
    displayOrder: number
  }
  answer?: {
    submittedContent?: string
    status: string
  }
}

type ReportViewState =
  | 'loading'
  | 'generating'
  | 'failed'
  | 'empty'
  | 'ready'
  | 'invalid'
  | 'load-error'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.interviewId))
const report = ref<InterviewReport | null>(null)
const mode = ref<InterviewMode>('QUICK')
const body = ref<ReportBody | null>(null)
const noReport = ref(false)
const history = ref<HistoryTurn[]>([])
const loading = ref(true)
const retrying = ref(false)
const loadError = ref('')
const reportReadError = ref(false)
const actionError = ref('')
let poll: number | undefined

const viewState = computed<ReportViewState>(() => {
  if (loadError.value) return 'load-error'
  if (reportReadError.value) return 'invalid'
  if (noReport.value) return 'empty'
  if (report.value?.status === 'FAILED') return 'failed'
  if (body.value) return 'ready'
  if (loading.value && !report.value) return 'loading'
  return 'generating'
})

const modeLabel = computed(() => (
  mode.value === 'COMPREHENSIVE' ? '综合面试报告' : '快速面试报告'
))

const reportUpdatedAt = computed(() => {
  if (!report.value?.updatedAt) return ''
  const timestamp = new Date(report.value.updatedAt)
  if (Number.isNaN(timestamp.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(timestamp)
})

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(poll))

async function load() {
  loading.value = true
  actionError.value = ''
  window.clearTimeout(poll)

  try {
    const session = await getInterview(id.value)
    mode.value = session.mode
    let currentReport: InterviewReport | null = null

    try {
      currentReport = await getInterviewReport(id.value)
    } catch (error) {
      if (toApiError(error).kind !== 'not-found') {
        throw error
      }
    }

    loadError.value = ''
    reportReadError.value = false
    report.value = currentReport
    body.value = null
    history.value = []
    noReport.value = !currentReport && session.stage === 'COMPLETED'

    if (noReport.value || currentReport?.status === 'FAILED') {
      return
    }

    if (currentReport?.status === 'READY') {
      try {
        body.value = parseReportBody(currentReport.reportJson)
      } catch {
        reportReadError.value = true
        return
      }
      history.value = await getInterviewHistory(id.value)
      return
    }

    poll = window.setTimeout(load, 2_500)
  } catch (error) {
    loadError.value = toApiError(error).message
  } finally {
    loading.value = false
  }
}

async function retry() {
  retrying.value = true
  actionError.value = ''

  try {
    report.value = await retryInterviewReport(id.value)
    body.value = null
    noReport.value = false
    reportReadError.value = false
    poll = window.setTimeout(load, 1_000)
  } catch (error) {
    actionError.value = toApiError(error).message
  } finally {
    retrying.value = false
  }
}

function parseReportBody(raw: string | undefined): ReportBody {
  if (!raw) {
    throw new Error('报告内容为空')
  }

  const parsed: unknown = JSON.parse(raw)
  if (!isRecord(parsed) || typeof parsed.summary !== 'string' || !parsed.summary.trim()) {
    throw new Error('报告摘要无效')
  }
  if (
    !Array.isArray(parsed.strengths)
    || !parsed.strengths.every(isFinding)
    || !Array.isArray(parsed.improvements)
    || !parsed.improvements.every(isFinding)
    || !Array.isArray(parsed.topicReviews)
    || !parsed.topicReviews.every(isTopicReview)
  ) {
    throw new Error('报告章节无效')
  }

  return {
    summary: parsed.summary,
    strengths: parsed.strengths,
    improvements: parsed.improvements,
    topicReviews: parsed.topicReviews,
  }
}

function isFinding(value: unknown): value is Finding {
  return isRecord(value)
    && typeof value.title === 'string'
    && Boolean(value.title.trim())
    && typeof value.description === 'string'
    && Boolean(value.description.trim())
    && (value.quote === undefined || typeof value.quote === 'string')
}

function isTopicReview(value: unknown): value is TopicReview {
  return isRecord(value)
    && Number.isInteger(value.sequenceNo)
    && Number(value.sequenceNo) > 0
    && typeof value.conclusion === 'string'
    && Boolean(value.conclusion.trim())
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
</script>

<template>
  <section class="report-page">
    <header class="report-header">
      <div class="report-header__copy">
        <p class="report-header__eyebrow">面试报告</p>
        <h1>{{ modeLabel }}</h1>
        <p>根据本场已提交回答整理，只评价实际考查到的内容。</p>
      </div>
      <Button
        data-testid="back-to-records"
        @click="router.push('/app/interview-records')"
      >
        返回面试记录
      </Button>
    </header>

    <div class="report-reading-column">
      <section
        v-if="viewState === 'loading'"
        class="report-state"
        role="status"
        aria-live="polite"
      >
        <Spin size="large" />
        <h2>正在读取面试报告</h2>
        <p>正在确认本场面试和报告状态。</p>
      </section>

      <section
        v-else-if="viewState === 'generating'"
        class="report-state"
        role="status"
        aria-live="polite"
      >
        <Spin size="large" />
        <p class="report-state__label">报告任务进行中</p>
        <h2>报告正在生成</h2>
        <p>面试已经结束，你可以先离开。生成完成后可从面试记录重新进入。</p>
      </section>

      <section v-else-if="viewState === 'failed'" class="report-state">
        <p class="report-state__label report-state__label--error">生成失败</p>
        <h2>报告生成未完成</h2>
        <p>题目和已提交回答不会改变，重试只会重新执行报告任务。</p>
        <p v-if="actionError" class="report-state__error" role="alert">{{ actionError }}</p>
        <Button
          type="primary"
          data-testid="retry-report"
          :loading="retrying"
          @click="retry"
        >
          重试生成
        </Button>
      </section>

      <section v-else-if="viewState === 'empty'" class="report-state">
        <p class="report-state__label">本场已结束</p>
        <h2>没有生成正式报告</h2>
        <p>本场没有可用于评价的已提交回答，因此不会生成没有依据的结论。</p>
      </section>

      <section v-else-if="viewState === 'invalid'" class="report-state">
        <p class="report-state__label report-state__label--error">读取失败</p>
        <h2>报告内容暂时无法读取</h2>
        <p>重新读取不会重新生成或修改已发布报告，也不会改变原始问答记录。</p>
        <Button
          type="primary"
          data-testid="reload-report"
          :loading="loading"
          @click="load"
        >
          重新读取
        </Button>
      </section>

      <section v-else-if="viewState === 'load-error'" class="report-state">
        <p class="report-state__label report-state__label--error">连接失败</p>
        <h2>暂时无法读取报告</h2>
        <p>{{ loadError }}</p>
        <Button
          type="primary"
          data-testid="reload-report"
          :loading="loading"
          @click="load"
        >
          重新读取
        </Button>
      </section>

      <template v-else-if="body">
        <p v-if="reportUpdatedAt" class="report-meta">
          报告更新于 <time :datetime="report?.updatedAt">{{ reportUpdatedAt }}</time>
        </p>

        <section class="report-summary">
          <p class="report-section-index">01</p>
          <h2>总结</h2>
          <p>{{ body.summary }}</p>
        </section>

        <InterviewReportSection
          index="02"
          title="做得好的地方"
          :items="body.strengths"
        />

        <InterviewReportSection
          index="03"
          title="优先改进项"
          :items="body.improvements"
        />

        <section class="report-section">
          <p class="report-section-index">04</p>
          <h2>考查回顾</h2>
          <div v-if="body.topicReviews.length" class="report-topic-list">
            <article
              v-for="topic in body.topicReviews"
              :key="topic.sequenceNo"
              class="report-topic"
            >
              <h3>
                {{ mode === 'COMPREHENSIVE' ? '主题' : '考查项' }}
                {{ topic.sequenceNo }}
              </h3>
              <p>{{ topic.conclusion }}</p>
            </article>
          </div>
          <p v-else class="report-empty">暂无可回顾的已考查主题。</p>
        </section>

        <section class="report-section report-history">
          <p class="report-section-index">05</p>
          <h2>完整问答记录</h2>
          <ol v-if="history.length" class="report-history__list">
            <li v-for="turn in history" :key="turn.question.questionId">
              <article class="report-turn">
                <p class="report-turn__number">问题 {{ turn.question.displayOrder }}</p>
                <h3>{{ turn.question.content }}</h3>
                <blockquote v-if="turn.answer?.submittedContent">
                  {{ turn.answer.submittedContent }}
                </blockquote>
                <p v-else class="report-turn__skipped">已跳过</p>
              </article>
            </li>
          </ol>
          <p v-else class="report-empty">暂无可展示的问答记录。</p>
        </section>
      </template>
    </div>
  </section>
</template>

<style scoped>
.report-page {
  display: grid;
  width: min(100%, 1080px);
  gap: var(--ssm-space-8);
  margin: 0 auto;
  padding-bottom: var(--ssm-space-16);
}

.report-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ssm-space-6);
  padding-bottom: var(--ssm-space-6);
  border-bottom: 1px solid var(--ssm-color-line);
}

.report-header__copy {
  max-width: 680px;
}

.report-header__eyebrow,
.report-state__label,
.report-section-index,
.report-turn__number {
  margin: 0 0 var(--ssm-space-2);
  color: var(--ssm-color-brand);
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.report-header h1 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-family: var(--ssm-font-serif);
  font-size: clamp(34px, 5vw, 54px);
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1.12;
}

.report-header__copy > p:last-child {
  margin: var(--ssm-space-3) 0 0;
  color: var(--ssm-color-text-secondary);
  line-height: 1.75;
}

.report-reading-column {
  display: grid;
  width: min(100%, 760px);
  gap: var(--ssm-space-8);
  margin: 0 auto;
}

.report-state {
  display: grid;
  min-height: 320px;
  place-items: center;
  align-content: center;
  gap: var(--ssm-space-3);
  padding: var(--ssm-space-12);
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-panel);
  background: var(--ssm-color-surface);
  text-align: center;
}

.report-state h2 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-family: var(--ssm-font-serif);
  font-size: 30px;
}

.report-state > p:not(.report-state__label) {
  max-width: 560px;
  margin: 0 0 var(--ssm-space-2);
  color: var(--ssm-color-text-secondary);
  line-height: 1.75;
}

.report-state__label--error,
.report-state__error {
  color: var(--ssm-color-error) !important;
}

.report-meta {
  margin: 0;
  color: var(--ssm-color-text-muted);
  font-size: 13px;
  text-align: right;
}

.report-summary,
.report-section {
  padding: clamp(24px, 4vw, 40px);
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-panel);
  background: var(--ssm-color-surface);
}

.report-summary {
  border-top: 3px solid var(--ssm-color-brand);
}

.report-summary h2,
.report-section h2 {
  margin: 0 0 var(--ssm-space-6);
  color: var(--ssm-color-ink);
  font-family: var(--ssm-font-serif);
  font-size: 28px;
}

.report-summary > p:last-child,
.report-topic p {
  margin: 0;
  color: var(--ssm-color-text-secondary);
  font-size: 16px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.report-topic-list {
  display: grid;
  gap: var(--ssm-space-6);
}

.report-topic + .report-topic {
  padding-top: var(--ssm-space-6);
  border-top: 1px solid var(--ssm-color-line);
}

.report-topic h3,
.report-turn h3 {
  margin: 0 0 var(--ssm-space-3);
  color: var(--ssm-color-ink);
  font-size: 17px;
  line-height: 1.55;
}

.report-empty {
  margin: 0;
  color: var(--ssm-color-text-muted);
  line-height: 1.75;
}

.report-history__list {
  display: grid;
  gap: var(--ssm-space-8);
  padding: 0;
  margin: 0;
  list-style: none;
}

.report-history__list > li + li {
  padding-top: var(--ssm-space-8);
  border-top: 1px solid var(--ssm-color-line);
}

.report-turn blockquote {
  margin: var(--ssm-space-4) 0 0;
  padding: var(--ssm-space-4) var(--ssm-space-6);
  border-left: 3px solid var(--ssm-color-brand);
  background: var(--ssm-color-brand-soft);
  color: var(--ssm-color-ink);
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.report-turn__skipped {
  width: fit-content;
  margin: var(--ssm-space-4) 0 0;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--ssm-color-paper);
  color: var(--ssm-color-text-muted);
  font-size: 13px;
}

@media (max-width: 680px) {
  .report-page {
    gap: var(--ssm-space-6);
  }

  .report-header {
    align-items: stretch;
    flex-direction: column;
  }

  .report-header > button {
    align-self: flex-start;
  }

  .report-state {
    min-height: 280px;
    padding: var(--ssm-space-8) var(--ssm-space-6);
  }

  .report-summary,
  .report-section {
    padding: var(--ssm-space-6);
  }

  .report-turn blockquote {
    padding-inline: var(--ssm-space-4);
  }
}
</style>
