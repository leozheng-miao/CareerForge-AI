<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Button, Spin, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getInterview,
  getInterviewHistory,
  getInterviewReport,
  retryInterviewReport,
  type InterviewReport,
} from '@/features/interview/api/interview-flow-api'
import InterviewReportSection from '@/features/interview/components/InterviewReportSection.vue'
import { toApiError } from '@/shared/api/errors'

interface Finding {
  title: string
  description: string
  quote?: string
}

interface ReportBody {
  summary: string
  strengths: Finding[]
  improvements: Finding[]
  topicReviews: Array<{ sequenceNo: number; conclusion: string }>
}

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.interviewId))
const report = ref<InterviewReport | null>(null)
const body = ref<ReportBody | null>(null)
const noReport = ref(false)
const history = ref<
  Array<{
    question: { questionId: string; content: string; displayOrder: number }
    answer?: { submittedContent?: string; status: string }
  }>
>([])
const loading = ref(true)
let poll: number | undefined

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(poll))

async function load() {
  loading.value = true
  window.clearTimeout(poll)

  try {
    const session = await getInterview(id.value)
    let currentReport: InterviewReport | null = null

    try {
      currentReport = await getInterviewReport(id.value)
    } catch (error) {
      if (toApiError(error).kind !== 'not-found') {
        throw error
      }
    }

    report.value = currentReport
    noReport.value = !currentReport && session.stage === 'COMPLETED'

    if (noReport.value) {
      history.value = await getInterviewHistory(id.value)
      return
    }

    if (currentReport?.status === 'FAILED') {
      return
    }

    if (currentReport?.status === 'READY' && currentReport.reportJson) {
      body.value = JSON.parse(currentReport.reportJson) as ReportBody
      history.value = await getInterviewHistory(id.value)
      return
    }

    poll = window.setTimeout(load, 2_500)
  } catch (error) {
    message.error(toApiError(error).message)
  } finally {
    loading.value = false
  }
}

async function retry() {
  try {
    report.value = await retryInterviewReport(id.value)
    body.value = null
    noReport.value = false
    poll = window.setTimeout(load, 1_000)
  } catch (error) {
    message.error(toApiError(error).message)
  }
}
</script>
<template><section class="report"><header><div><p class="eyebrow">FORMAL INTERVIEW REPORT</p><h1>快速面试报告</h1></div><Button @click="router.push('/app/interview-records')">返回面试记录</Button></header><section v-if="noReport" class="state"><h2>本场面试已结束</h2><p>没有可用的已提交回答，因此不生成正式报告。</p></section><section v-else-if="!body && report?.status==='FAILED'" class="state"><h2>报告生成未完成</h2><p>题目和已冻结回答不会改变，只重试报告任务。</p><Button type="primary" @click="retry">重试生成</Button></section><section v-else-if="!body" class="state"><Spin size="large"/><h2>正在生成正式面试报告…</h2><p>你可以离开此页，后台任务会继续执行。</p></section><template v-else><section class="summary"><h2>总结</h2><p>{{ body.summary }}</p></section><InterviewReportSection title="做得好的地方" :items="body.strengths"/><InterviewReportSection title="可以继续提升" :items="body.improvements"/><section class="summary"><h2>考查回顾</h2><article v-for="topic in body.topicReviews" :key="topic.sequenceNo"><strong>考查项 {{ topic.sequenceNo }}</strong><p>{{ topic.conclusion }}</p></article></section><section class="summary"><h2>完整问答记录</h2><article v-for="turn in history" :key="turn.question.questionId"><h3>{{ turn.question.content }}</h3><p>{{ turn.answer?.submittedContent || '已跳过' }}</p></article></section></template></section></template>
<style scoped>.report{display:grid;gap:20px;max-width:980px;margin:auto}.report>header{display:flex;justify-content:space-between;align-items:flex-start}.eyebrow{color:var(--cf-color-primary);font-weight:750}h1{margin:3px 0;color:#0b1740}.state,.summary{padding:30px;border:1px solid var(--cf-color-border);border-radius:16px;background:#fff;text-align:center}.summary{text-align:left}.summary p{line-height:1.8;color:var(--cf-color-text-secondary)}.summary article+article{margin-top:20px;padding-top:20px;border-top:1px solid var(--cf-color-border)}</style>
