<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Spin, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/features/auth/store/auth-store'
import { observeInterviewFlow } from '@/features/interview/api/interview-flow-sse'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { ROUTE_PATHS } from '@/router/paths'
import { toApiError } from '@/shared/api/errors'

const route = useRoute()
const router = useRouter()
const store = useInterviewFlowStore()
const auth = useAuthStore()
const id = computed(() => String(route.params.interviewId))
const ready = computed(() => store.session?.stage === 'READY')
const preparing = computed(() => store.session?.stage === 'PREPARING')
const comprehensive = computed(() => store.session?.mode === 'COMPREHENSIVE')
const entering = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
let poll: number | undefined
let stream: AbortController | null = null

const durationLabel = computed(() => {
  if (!comprehensive.value) return '60 分钟'
  if (store.comprehensiveSetup?.durationSeconds === 5400) return '90 分钟'
  if (store.comprehensiveSetup?.durationSeconds === 3600) return '60 分钟'
  return '已按本场设置保存'
})
const difficultyLabel = computed(() => {
  const difficulty = store.comprehensiveSetup?.difficulty
  if (!difficulty) return '已按本场设置保存'
  return { BASIC: '基础', STANDARD: '标准', CHALLENGE: '挑战' }[difficulty]
})

onMounted(async () => {
  await refresh()
  if (preparing.value || ready.value) connect()
})
onBeforeUnmount(() => {
  stopPolling()
  stream?.abort()
})

async function refresh() {
  if (refreshing.value) return
  refreshing.value = true
  errorMessage.value = ''
  try {
    const session = await store.restore(id.value)
    if (!session) return
    if (session.stage === 'INTERVIEWING') {
      stopPolling()
      await router.replace(`/app/interviews/${id.value}/session`)
      return
    }
    if (['GENERATING_REPORT', 'COMPLETED', 'FAILED'].includes(session.stage)) {
      stopPolling()
      await router.replace(`/app/interviews/${id.value}/report`)
      return
    }
    synchronizePolling()
  } catch (error) {
    stopPolling()
    errorMessage.value = toApiError(error).message
  } finally {
    refreshing.value = false
  }
}

function synchronizePolling() {
  stopPolling()
  if (preparing.value) poll = window.setInterval(refresh, 2_000)
}

function stopPolling() {
  window.clearInterval(poll)
  poll = undefined
}

function connect() {
  const actorId = auth.currentUser?.userId
  if (!actorId) return
  stream?.abort()
  stream = new AbortController()
  void observeInterviewFlow(actorId, id.value, stream.signal, async (event, name) => {
    if (name === 'preparation_ready' || event.status === 'READY') await refresh()
  }).catch(() => undefined)
}

async function enter() {
  if (!ready.value || entering.value) return
  entering.value = true
  try {
    await store.enter()
    await router.replace(`/app/interviews/${id.value}/session`)
  } catch (error) {
    message.error(toApiError(error).message)
  } finally {
    entering.value = false
  }
}

function edit() {
  void router.push(`/app/interviews/comprehensive/setup?interviewId=${encodeURIComponent(id.value)}`)
}
</script>

<template>
  <section class="ready-page">
    <header class="ready-page__navigation">
      <Button type="text" @click="router.push(ROUTE_PATHS.interviews)">
        <ArrowLeftOutlined aria-hidden="true" />
        返回面试中心
      </Button>
    </header>

    <main class="ready-card" :class="{ 'ready-card--complete': ready }">
      <div class="ready-card__status" aria-live="polite">
        <span v-if="errorMessage" class="ready-card__status-icon ready-card__status-icon--error">!</span>
        <span v-else-if="ready" class="ready-card__status-icon ready-card__status-icon--complete">
          <CheckCircleOutlined aria-hidden="true" />
        </span>
        <span v-else class="ready-card__status-icon"><Spin size="small" /></span>
        <p>{{ errorMessage ? '状态读取失败' : ready ? '面试已就绪' : '正在准备' }}</p>
      </div>

      <div class="ready-card__copy">
        <h1>{{ ready ? '准备好后，由你确认正式开始。' : '正在整理这场面试。' }}</h1>
        <p v-if="ready">正式开始后才会展示第一道问题并开始计算有效作答时间。</p>
        <p v-else>
          {{ comprehensive ? '正在根据岗位和你确认的材料准备专属面试。' : '正在为 AI Agent 开发方向准备面试。' }}
          你可以先离开，稍后从面试记录回来。
        </p>
      </div>

      <Alert
        v-if="errorMessage"
        type="error"
        show-icon
        :message="errorMessage"
        description="面试记录仍然保留，可以重新读取当前状态。"
      >
        <template #action>
          <Button size="small" :loading="refreshing" @click="refresh">重试</Button>
        </template>
      </Alert>

      <dl class="ready-card__facts">
        <div><dt>面试模式</dt><dd>{{ comprehensive ? '综合面试' : '快速面试' }}</dd></div>
        <div><dt>岗位方向</dt><dd>AI Agent 开发</dd></div>
        <div><dt>有效作答时长</dt><dd>{{ durationLabel }}</dd></div>
        <div v-if="comprehensive"><dt>难度</dt><dd>{{ difficultyLabel }}</dd></div>
        <div v-else><dt>考查范围</dt><dd>8 个考查目标</dd></div>
      </dl>

      <p class="ready-card__notice">
        <ClockCircleOutlined aria-hidden="true" />
        准备和等待下一题的时间不计入有效作答时长，具体题目只在正式面试中逐题展示。
      </p>

      <footer class="ready-card__actions">
        <Button v-if="comprehensive" size="large" @click="edit">返回修改设置</Button>
        <Button
          data-testid="start-interview"
          type="primary"
          size="large"
          :disabled="!ready || Boolean(errorMessage)"
          :loading="entering"
          @click="enter"
        >
          正式开始
        </Button>
      </footer>
    </main>
  </section>
</template>

<style scoped>
.ready-page { width: min(100%, 900px); margin: 0 auto; padding: 8px 0 64px; }
.ready-page__navigation { margin-bottom: 36px; }
.ready-card { display: grid; gap: 30px; padding: clamp(28px, 6vw, 58px); border: 1px solid var(--ssm-color-line); border-radius: var(--ssm-radius-card); background: var(--ssm-color-surface); }
.ready-card__status { display: flex; align-items: center; gap: 10px; color: var(--ssm-color-warning); font-size: 14px; font-weight: 700; }
.ready-card--complete .ready-card__status { color: var(--ssm-color-success); }
.ready-card__status p { margin: 0; }
.ready-card__status-icon { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 50%; background: #f5ebdd; }
.ready-card__status-icon--complete { background: #e4f1e9; font-size: 18px; }
.ready-card__status-icon--error { background: #f7e7e5; color: var(--ssm-color-error); }
.ready-card__copy { max-width: 720px; }
.ready-card__copy h1 { margin: 0; color: var(--ssm-color-ink); font-size: clamp(34px, 5vw, 54px); letter-spacing: -0.045em; line-height: 1.15; }
.ready-card__copy p { margin: 18px 0 0; color: var(--ssm-color-text-secondary); font-size: 16px; line-height: 1.8; }
.ready-card__facts { display: grid; grid-template-columns: repeat(2, 1fr); margin: 0; border-top: 1px solid var(--ssm-color-line); border-left: 1px solid var(--ssm-color-line); }
.ready-card__facts div { display: grid; gap: 7px; padding: 18px 20px; border-right: 1px solid var(--ssm-color-line); border-bottom: 1px solid var(--ssm-color-line); }
.ready-card__facts dt { color: var(--ssm-color-text-secondary); font-size: 12px; }
.ready-card__facts dd { margin: 0; color: var(--ssm-color-ink); font-weight: 700; }
.ready-card__notice { display: flex; align-items: flex-start; gap: 9px; margin: 0; color: var(--ssm-color-text-secondary); line-height: 1.7; }
.ready-card__notice :deep(svg) { margin-top: 5px; color: var(--ssm-color-brand); }
.ready-card__actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
@media (max-width: 640px) { .ready-card__facts { grid-template-columns: 1fr; } .ready-card__actions { align-items: stretch; flex-direction: column-reverse; } }
</style>
