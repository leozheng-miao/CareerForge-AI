<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowLeftOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons-vue'
import { Button, Empty, Input, message as toast } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

import TypewriterMarkdown from '@/components/content/TypewriterMarkdown.vue'
import {
  extractMemoriesFromTurns,
  isCoachRunTerminal,
  loadCoachRun,
  loadCoachWorkspace,
  loadOlderCoachTurns,
  mergeTurns,
  submitCoachRun,
} from '@/features/coach/api/coach-api'
import { clearCoachRunResume, observeCoachRun } from '@/features/coach/api/coach-sse'
import { useAuthStore } from '@/features/auth/store/auth-store'
import type {
  CoachingRunResponse,
  CoachingSessionResponse,
  CoachingTurnResponse,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sessionId = computed(() => String(route.params.sessionId ?? ''))
const session = ref<CoachingSessionResponse | null>(null)
const turns = ref<CoachingTurnResponse[]>([])
const runs = ref<CoachingRunResponse[]>([])
const olderCursor = ref<string | null>(null)
const hasOlder = ref(false)
const messageText = ref('')
const loading = ref(false)
const sending = ref(false)
const extracting = ref(false)
const reconnectAttempt = ref(0)
const streamError = ref('')
const pendingRequestId = ref<string | null>(null)
const pendingMessage = ref('')
const optimisticUserMessage = ref<{
  requestId: string
  content: string
  state: 'submitting' | 'accepted' | 'uncertain'
} | null>(null)
const awaitingTerminalTurns = ref(false)
const typewriterTurnId = ref<string | null>(null)
const messagesElement = ref<HTMLElement | null>(null)
let streamController: AbortController | null = null

const activeRun = computed(() => runs.value.find((run) => !isCoachRunTerminal(run)))
const assistantReplyPending = computed(
  () => sending.value || Boolean(activeRun.value) || awaitingTerminalTurns.value,
)
const canSend = computed(
  () =>
    session.value?.status === 'ACTIVE' &&
    !activeRun.value &&
    !sending.value &&
    !pendingRequestId.value,
)

onMounted(reload)
onBeforeUnmount(() => streamController?.abort())

async function reload() {
  loading.value = true
  streamError.value = ''
  try {
    const workspace = await loadCoachWorkspace(sessionId.value)
    session.value = workspace.session
    turns.value = workspace.turns
    runs.value = workspace.runs
    reconcileOptimisticMessage(workspace.turns, workspace.runs)
    olderCursor.value = workspace.turnsCursor
    hasOlder.value = workspace.hasOlderTurns
    const unfinished = workspace.runs.find((run) => !isCoachRunTerminal(run) && run.runId)
    if (unfinished?.runId) connectRun(unfinished.runId)
    await scrollToBottom()
  } catch (error) {
    streamError.value = error instanceof Error ? error.message : toApiError(error).message
  } finally {
    loading.value = false
  }
}

async function sendMessage(retry = false) {
  if (!session.value || activeRun.value || sending.value || (!retry && !canSend.value)) return
  sending.value = true
  streamError.value = ''
  const requestId = pendingRequestId.value ?? crypto.randomUUID()
  const submittedMessage = pendingRequestId.value ? pendingMessage.value : messageText.value
  if (!submittedMessage.trim()) {
    sending.value = false
    return
  }
  pendingRequestId.value = requestId
  pendingMessage.value = submittedMessage.trim()
  if (!retry) {
    optimisticUserMessage.value = {
      requestId,
      content: pendingMessage.value,
      state: 'submitting',
    }
    messageText.value = ''
    await scrollToBottom()
  }
  try {
    const run = await submitCoachRun(session.value, submittedMessage, requestId)
    pendingRequestId.value = null
    pendingMessage.value = ''
    if (optimisticUserMessage.value?.requestId === requestId) {
      optimisticUserMessage.value.state = 'accepted'
    }
    runs.value = [run, ...runs.value.filter((item) => item.runId !== run.runId)]
    if (run.runId) connectRun(run.runId)
    await scrollToBottom()
  } catch (error) {
    toast.error(toApiError(error).message)
    await reload()
    const recovered = runs.value.find((run) => run.requestId === requestId)
    if (recovered) {
      pendingRequestId.value = null
      pendingMessage.value = ''
      if (optimisticUserMessage.value?.requestId === requestId) {
        optimisticUserMessage.value.state = 'accepted'
      }
      toast.info('已通过 requestId 找回后端 Run，不会重复提交')
    } else {
      if (optimisticUserMessage.value?.requestId === requestId) {
        optimisticUserMessage.value.state = 'uncertain'
      }
      streamError.value = '提交结果仍未确认，可使用原 requestId 安全重试'
    }
  } finally {
    sending.value = false
  }
}

function connectRun(runId: string) {
  streamController?.abort()
  reconnectAttempt.value = 0
  const actorId = authStore.currentUser?.userId
  if (!actorId) {
    streamError.value = '当前用户身份摘要缺失，无法安全保存事件断点'
    return
  }
  const controller = new AbortController()
  streamController = controller
  void observeCoachRun(actorId, runId, controller.signal, {
    onEvent() {
      reconnectAttempt.value = 0
    },
    async onTerminal() {
      const run = await loadCoachRun(runId)
      runs.value = [run, ...runs.value.filter((item) => item.runId !== runId)]
      if (isCoachRunTerminal(run)) {
        clearCoachRunResume(actorId, runId)
        await refreshCompletedRun(run, true)
      }
    },
    onReconnect(attempt) {
      reconnectAttempt.value = attempt
    },
  }).catch(async (error) => {
    if (controller.signal.aborted) return
    streamError.value = error instanceof Error ? error.message : toApiError(error).message
    try {
      const run = await loadCoachRun(runId)
      runs.value = [run, ...runs.value.filter((item) => item.runId !== runId)]
      if (isCoachRunTerminal(run)) await refreshCompletedRun(run, true)
    } catch {
      /* REST 回读失败时保留 Run ID 与 Last-Event-ID。 */
    }
  })
}

async function loadOlder() {
  if (!olderCursor.value) return
  try {
    const page = await loadOlderCoachTurns(sessionId.value, olderCursor.value)
    turns.value = mergeTurns(page.items, turns.value)
    olderCursor.value = page.nextCursor
    hasOlder.value = page.hasMore
  } catch (error) {
    toast.error(toApiError(error).message)
  }
}

async function extractMemories() {
  extracting.value = true
  try {
    const candidates = await extractMemoriesFromTurns(sessionId.value, turns.value)
    toast.success(`已提取 ${candidates.length} 条记忆候选，请到长期记忆页审阅`)
  } catch (error) {
    toast.error(toApiError(error).message)
  } finally {
    extracting.value = false
  }
}

async function refreshCompletedRun(run: CoachingRunResponse, animateAnswer: boolean) {
  awaitingTerminalTurns.value = true
  const knownTurnIds = new Set(turns.value.map((turn) => turn.turnId).filter(Boolean))
  try {
    const workspace = await loadCoachWorkspace(sessionId.value)
    const assistantTurn = workspace.turns.find(
      (turn) =>
        turn.turnId === run.assistantTurnId &&
        turn.status === 'COMPLETED' &&
        !knownTurnIds.has(turn.turnId),
    )
    typewriterTurnId.value = animateAnswer ? (assistantTurn?.turnId ?? null) : null
    session.value = workspace.session
    turns.value = workspace.turns
    runs.value = workspace.runs
    olderCursor.value = workspace.turnsCursor
    hasOlder.value = workspace.hasOlderTurns
    reconcileOptimisticMessage(workspace.turns, workspace.runs)
  } finally {
    awaitingTerminalTurns.value = false
    await scrollToBottom()
  }
}

function reconcileOptimisticMessage(
  persistedTurns: CoachingTurnResponse[],
  availableRuns: CoachingRunResponse[],
) {
  const optimistic = optimisticUserMessage.value
  if (!optimistic) return
  const matchingRun = availableRuns.find((run) => run.requestId === optimistic.requestId)
  if (
    matchingRun?.userTurnId &&
    persistedTurns.some((turn) => turn.turnId === matchingRun.userTurnId)
  ) {
    optimisticUserMessage.value = null
  }
}

async function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
  await nextTick()
  messagesElement.value?.scrollTo({ top: messagesElement.value.scrollHeight, behavior })
}
</script>

<template>
  <section class="coach-workspace">
    <header class="workspace-header">
      <div>
        <Button type="link" class="back" @click="router.push('/app/coach')"
          ><ArrowLeftOutlined />返回会话</Button
        >
        <h2>{{ session?.title || 'Career Coach' }}</h2>
        <p>Session {{ sessionId }} · v{{ session?.version ?? '-' }}</p>
      </div>
      <div class="header-actions">
        <Button :loading="extracting" @click="extractMemories"
          ><DatabaseOutlined />提取记忆候选</Button
        >
        <Button :loading="loading" @click="reload"><ReloadOutlined />REST 回读</Button>
      </div>
    </header>

    <div v-if="streamError" class="notice notice--error">
      {{ streamError }}；Run ID 与断点已保留，可点击 REST 回读。
      <Button
        v-if="pendingRequestId && !activeRun"
        size="small"
        danger
        @click="() => sendMessage(true)"
      >
        使用原 requestId 重试
      </Button>
    </div>
    <div class="chat-panel">
      <div ref="messagesElement" class="messages" aria-live="polite">
        <Button v-if="hasOlder" class="older" size="small" @click="loadOlder">加载更早对话</Button>
        <Empty
          v-if="!loading && !turns.length && !optimisticUserMessage && !assistantReplyPending"
          description="发送第一条消息，开始基于证据的复盘"
        />
        <article
          v-for="turn in turns"
          :key="turn.turnId"
          class="turn"
          :class="`turn--${turn.role?.toLowerCase()}`"
        >
          <div class="turn__role">{{ turn.role === 'USER' ? '你' : 'Career Coach' }}</div>
          <div class="turn__body">
            <TypewriterMarkdown
              v-if="turn.status === 'COMPLETED'"
              :content="turn.content"
              :play="turn.turnId === typewriterTurnId"
              @progress="scrollToBottom('auto')"
              @complete="typewriterTurnId = null"
            />
            <span v-else class="failed">这条消息未成功保存内容</span>
          </div>
        </article>
        <article v-if="optimisticUserMessage" class="turn turn--user turn--optimistic">
          <div class="turn__role">
            你
            <span class="turn__delivery">{{
              optimisticUserMessage.state === 'uncertain' ? '待确认' : '已发送'
            }}</span>
          </div>
          <div class="turn__body">{{ optimisticUserMessage.content }}</div>
        </article>
        <article v-if="assistantReplyPending" class="turn turn--assistant turn--typing">
          <div class="turn__role">Career Coach</div>
          <div class="turn__body">
            <span class="cf-visually-hidden">Career Coach 正在回复</span>
            <span class="typing-dots" aria-hidden="true"><i /><i /><i /></span>
            <span v-if="reconnectAttempt" class="reconnect">连接恢复中</span>
          </div>
        </article>
      </div>
      <div class="composer">
        <Input.TextArea
          v-model:value="messageText"
          :maxlength="8000"
          :auto-size="{ minRows: 2, maxRows: 7 }"
          placeholder="描述你当前的目标、困难或本周复盘…"
          :disabled="!canSend"
          @keydown.meta.enter.prevent="() => sendMessage()"
          @keydown.ctrl.enter.prevent="() => sendMessage()"
        />
        <div class="composer__footer">
          <span>{{ activeRun ? '当前 Run 完成前不重复提交' : 'Ctrl / ⌘ + Enter 发送' }}</span>
          <Button
            type="primary"
            :loading="sending"
            :disabled="!canSend || !messageText.trim()"
            @click="() => sendMessage()"
            ><SendOutlined />发送</Button
          >
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.coach-workspace {
  display: grid;
  gap: 18px;
}
.workspace-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}
.workspace-header h2 {
  margin: 2px 0 5px;
  color: #0b1740;
  font-size: 28px;
}
.workspace-header p {
  margin: 0;
  color: var(--cf-color-text-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.back {
  height: auto;
  padding: 0;
}
.header-actions {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.notice {
  padding: 12px 14px;
  border-radius: 10px;
}
.notice--error {
  background: #fef2f2;
  color: #b91c1c;
}
.chat-panel {
  overflow: hidden;
  border: 1px solid var(--cf-color-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--cf-shadow-card);
}
.messages {
  display: grid;
  max-height: calc(100vh - 360px);
  min-height: 420px;
  gap: 20px;
  overflow-y: auto;
  padding: 28px;
}
.older {
  justify-self: center;
}
.turn {
  display: grid;
  max-width: 82%;
  gap: 7px;
}
.turn--user {
  justify-self: end;
}
.turn__role {
  color: var(--cf-color-text-muted);
  font-size: 12px;
  font-weight: 650;
}
.turn--user .turn__role {
  text-align: right;
}
.turn__body {
  padding: 15px 17px;
  border-radius: 14px;
  background: #f8fafc;
}
.turn--user .turn__body {
  background: #eef2ff;
}
.turn--optimistic .turn__body {
  white-space: pre-wrap;
}
.turn__delivery {
  margin-left: 5px;
  color: #818cf8;
  font-size: 11px;
  font-weight: 500;
}
.turn--typing .turn__body {
  display: flex;
  min-width: 74px;
  align-items: center;
  gap: 12px;
}
.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.typing-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #818cf8;
  animation: typing-bounce 1.2s ease-in-out infinite;
}
.typing-dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.typing-dots i:nth-child(3) {
  animation-delay: 0.3s;
}
.reconnect {
  color: var(--cf-color-text-muted);
  font-size: 11px;
}
@keyframes typing-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
.failed {
  color: var(--cf-color-error);
}
.composer {
  padding: 18px;
  border-top: 1px solid var(--cf-color-border);
  background: #fbfdff;
}
.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}
@media (max-width: 720px) {
  .workspace-header {
    flex-direction: column;
  }
  .turn {
    max-width: 95%;
  }
}
</style>
