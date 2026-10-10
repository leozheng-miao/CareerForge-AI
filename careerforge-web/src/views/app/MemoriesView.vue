<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleOutlined,
  DatabaseOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SwapOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Empty, Input, Modal, Skeleton, Tag } from 'ant-design-vue'

import {
  applyMemoryDecision,
  loadMemoryWorkspace,
  MemoryStateChangedError,
  type MemoryDecisionAction,
  type MemoryWorkspace,
} from '@/features/memory/api/memory-api'
import type {
  MemoryCandidateResponse,
  MemoryCandidateResponseSourceType,
  MemoryCandidateResponseType,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

type ConfirmedFilter = 'ALL' | MemoryCandidateResponseType

const workspace = ref<MemoryWorkspace | null>(null)
const loading = ref(true)
const deciding = ref(false)
const pageError = ref('')
const operationError = ref('')
const operationSuccess = ref('')
const decisionOpen = ref(false)
const decisionAction = ref<MemoryDecisionAction>('confirm')
const decisionMemory = ref<MemoryCandidateResponse | null>(null)
const decisionNote = ref('')
const confirmedFilter = ref<ConfirmedFilter>('ALL')

const pending = computed(() => workspace.value?.pending ?? [])
const confirmed = computed(() => workspace.value?.confirmed ?? [])
const skillProfileVersion = computed(() => workspace.value?.skillProfile.profileVersion ?? 0)
const weeklyHoursMemory = computed(() =>
  confirmed.value.find(
    (memory) => memory.type === 'TIME_CONSTRAINT' && memory.normalizedKey === 'weekly_hours',
  ),
)
const confirmedSkillsCount = computed(
  () => workspace.value?.skillProfile.skillEvidence?.length ?? 0,
)
const filteredConfirmed = computed(() => {
  if (confirmedFilter.value === 'ALL') return confirmed.value
  return confirmed.value.filter((memory) => memory.type === confirmedFilter.value)
})
const replacementExisting = computed(() => {
  const supersedesId = decisionMemory.value?.supersedesId
  return supersedesId
    ? (confirmed.value.find((memory) => memory.memoryId === supersedesId) ?? null)
    : null
})

const confirmedFilters: Array<{ value: ConfirmedFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'TIME_CONSTRAINT', label: '时间约束' },
  { value: 'LEARNING_PREFERENCE', label: '学习偏好' },
  { value: 'SKILL_EVIDENCE', label: '技能证据' },
  { value: 'CAREER_GOAL', label: '职业目标' },
]

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  pageError.value = ''
  try {
    workspace.value = await loadMemoryWorkspace()
  } catch (error) {
    pageError.value = toApiError(error).message
  } finally {
    loading.value = false
  }
}

function openDecision(action: MemoryDecisionAction, memory: MemoryCandidateResponse) {
  decisionAction.value = action
  decisionMemory.value = memory
  decisionNote.value = ''
  operationError.value = ''
  operationSuccess.value = ''
  decisionOpen.value = true
}

function closeDecision() {
  if (deciding.value) return
  decisionOpen.value = false
  decisionMemory.value = null
  decisionNote.value = ''
}

async function submitDecision() {
  const memory = decisionMemory.value
  if (!memory) return
  if (decisionNote.value.length > 500) {
    operationError.value = '决策说明不能超过 500 个字符'
    return
  }

  deciding.value = true
  operationError.value = ''
  try {
    await applyMemoryDecision(decisionAction.value, memory, decisionNote.value)
    workspace.value = await loadMemoryWorkspace()
    operationSuccess.value = decisionSuccessMessage(decisionAction.value, memory)
    decisionOpen.value = false
    decisionMemory.value = null
    decisionNote.value = ''
  } catch (error) {
    if (error instanceof MemoryStateChangedError) {
      workspace.value = error.latestWorkspace
      operationError.value = error.message
      decisionOpen.value = false
    } else {
      operationError.value = toApiError(error).message
    }
  } finally {
    deciding.value = false
  }
}

function canConfirm(memory: MemoryCandidateResponse) {
  if (memory.sourceType !== 'CONVERSATION_TURN') return false
  return (
    !memory.supersedesId || confirmed.value.some((item) => item.memoryId === memory.supersedesId)
  )
}

function decisionSuccessMessage(action: MemoryDecisionAction, memory: MemoryCandidateResponse) {
  if (action === 'reject') return '候选已拒绝，不会进入长期画像。'
  if (action === 'revoke') return 'Memory 已撤销，不再参与后续业务输入。'
  return memory.supersedesId
    ? '新候选已确认，并在同一事务中替代旧 Memory。'
    : '候选已确认并进入长期画像。'
}

function decisionTitle() {
  if (decisionAction.value === 'reject') return '拒绝这条 Memory 候选？'
  if (decisionAction.value === 'revoke') return '撤销这条已确认 Memory？'
  return decisionMemory.value?.supersedesId ? '确认新值并替代旧 Memory？' : '确认进入长期画像？'
}

function decisionConfirmText() {
  if (decisionAction.value === 'reject') return '确认拒绝'
  if (decisionAction.value === 'revoke') return '确认撤销'
  return decisionMemory.value?.supersedesId ? '确认替代' : '确认 Memory'
}

function typeMeta(type?: MemoryCandidateResponseType) {
  const metadata: Record<MemoryCandidateResponseType, { label: string; color: string }> = {
    CAREER_GOAL: { label: '职业目标', color: 'blue' },
    SKILL_EVIDENCE: { label: '技能证据', color: 'green' },
    LEARNING_PREFERENCE: { label: '学习偏好', color: 'purple' },
    TIME_CONSTRAINT: { label: '时间约束', color: 'gold' },
  }
  return type ? metadata[type] : { label: '未知类型', color: 'default' }
}

function sourceLabel(source?: MemoryCandidateResponseSourceType) {
  const labels: Record<MemoryCandidateResponseSourceType, string> = {
    CONVERSATION_TURN: 'Career Coach 对话',
    AGENT_RUN: 'Agent Run',
    JOB_DOCUMENT: '岗位文档',
    PROJECT_EVIDENCE: '项目证据',
    INTERVIEW_REPORT: '面试报告',
  }
  return source ? labels[source] : '来源待确认'
}

function confidenceLabel(value?: number) {
  if (value === undefined) return '模型置信度未提供'
  return `模型提取置信度 ${Math.round(value * 100)}%，仅供审阅`
}

function formatTime(value?: string) {
  if (!value) return '暂无时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间待确认'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <div class="memory-page">
    <header class="memory-hero">
      <div>
        <p>可信能力底座</p>
        <h2>由你决定，哪些信息可以进入长期画像</h2>
        <span>模型只能提出候选；确认、拒绝、替代和撤销都需要你的明确操作。</span>
      </div>
      <Button :loading="loading" @click="loadPage"><ReloadOutlined />刷新画像</Button>
    </header>

    <Alert v-if="pageError" type="error" show-icon :message="pageError">
      <template #action><Button size="small" @click="loadPage">重试</Button></template>
    </Alert>
    <Alert v-if="operationError" type="error" show-icon :message="operationError" closable />
    <Alert v-if="operationSuccess" type="success" show-icon :message="operationSuccess" closable />

    <section v-if="loading && !workspace" class="memory-loading">
      <Skeleton active :paragraph="{ rows: 8 }" />
    </section>

    <template v-else-if="workspace">
      <section class="memory-readiness" aria-labelledby="memory-readiness-title">
        <header>
          <div>
            <p>训练计划前置条件</p>
            <h3 id="memory-readiness-title">每周可用学习时间</h3>
          </div>
          <Tag :color="weeklyHoursMemory ? 'green' : 'orange'">
            <CheckCircleFilled v-if="weeklyHoursMemory" />
            <WarningOutlined v-else />
            {{ weeklyHoursMemory ? '已确认' : '尚未确认' }}
          </Tag>
        </header>
        <p v-if="weeklyHoursMemory" class="memory-readiness__value">
          {{ weeklyHoursMemory.content }}
        </p>
        <p v-else class="memory-readiness__empty">
          训练计划生成需要一条已确认的 weekly_hours
          时间约束。请在待确认候选中核对；若尚无候选，需先从 Career Coach 的已完成对话提取。
        </p>
        <footer>
          <span><DatabaseOutlined />有效 Memory {{ confirmed.length }} 条</span>
          <span><SafetyCertificateOutlined />技能画像版本 {{ skillProfileVersion }}</span>
          <span>技能证据 {{ confirmedSkillsCount }} 条</span>
        </footer>
      </section>

      <section class="pending-section" aria-labelledby="pending-title">
        <header class="section-heading">
          <div>
            <p>需要你的决定</p>
            <h3 id="pending-title">待确认候选</h3>
          </div>
          <span>{{ pending.length }} 条</span>
        </header>

        <Empty
          v-if="!pending.length"
          description="当前没有待确认候选；候选由 Career Coach 已完成对话显式提取"
        />
        <div v-else class="memory-list memory-list--pending">
          <article v-for="memory in pending" :key="memory.memoryId" class="memory-card">
            <header>
              <div>
                <Tag :color="typeMeta(memory.type).color">{{ typeMeta(memory.type).label }}</Tag>
                <Tag v-if="memory.supersedesId" color="orange"><SwapOutlined />替代候选</Tag>
              </div>
              <span>版本 {{ memory.version ?? '-' }}</span>
            </header>
            <p class="memory-card__content">{{ memory.content || '候选内容缺失' }}</p>
            <div class="memory-card__meta">
              <span>{{ sourceLabel(memory.sourceType) }}</span>
              <span>{{ confidenceLabel(memory.extractionConfidence) }}</span>
              <span><ClockCircleOutlined />{{ formatTime(memory.createdAt) }}</span>
            </div>
            <Alert
              v-if="memory.sourceType !== 'CONVERSATION_TURN'"
              type="warning"
              show-icon
              message="当前后端仅允许确认来源可追溯的 Career Coach 对话候选"
            />
            <Alert
              v-else-if="
                memory.supersedesId &&
                !confirmed.some((item) => item.memoryId === memory.supersedesId)
              "
              type="warning"
              show-icon
              message="未找到待替代的旧 Memory，请刷新后重试"
            />
            <footer>
              <Button danger @click="openDecision('reject', memory)">
                <CloseCircleOutlined />拒绝
              </Button>
              <Button
                type="primary"
                :disabled="!canConfirm(memory)"
                @click="openDecision('confirm', memory)"
              >
                <SwapOutlined v-if="memory.supersedesId" />
                <CheckCircleFilled v-else />
                {{ memory.supersedesId ? '确认并替代' : '确认进入画像' }}
              </Button>
            </footer>
          </article>
        </div>
      </section>

      <section class="confirmed-section" aria-labelledby="confirmed-title">
        <header class="section-heading">
          <div>
            <p>当前业务有效输入</p>
            <h3 id="confirmed-title">已确认长期画像</h3>
          </div>
          <span>{{ confirmed.length }} 条</span>
        </header>

        <div class="memory-filters" aria-label="筛选已确认 Memory">
          <button
            v-for="filter in confirmedFilters"
            :key="filter.value"
            type="button"
            :class="{ 'memory-filter--active': confirmedFilter === filter.value }"
            @click="confirmedFilter = filter.value"
          >
            {{ filter.label }}
          </button>
        </div>

        <Empty v-if="!filteredConfirmed.length" description="当前分类没有已确认 Memory" />
        <div v-else class="memory-list">
          <article v-for="memory in filteredConfirmed" :key="memory.memoryId" class="memory-card">
            <header>
              <Tag :color="typeMeta(memory.type).color">{{ typeMeta(memory.type).label }}</Tag>
              <Tag color="green"><CheckCircleFilled />已确认</Tag>
            </header>
            <p class="memory-card__content">{{ memory.content || 'Memory 内容缺失' }}</p>
            <div class="memory-card__meta">
              <span>槽位：{{ memory.normalizedKey || '待确认' }}</span>
              <span>{{ sourceLabel(memory.sourceType) }}</span>
              <span>版本 {{ memory.version ?? '-' }}</span>
            </div>
            <footer>
              <Button danger type="text" @click="openDecision('revoke', memory)">
                撤销此 Memory
              </Button>
            </footer>
          </article>
        </div>
      </section>

      <aside class="memory-principle">
        <ExclamationCircleOutlined />
        <div>
          <h3>模型置信度不是事实依据</h3>
          <p>长期画像只使用用户已确认且来源可追溯的 Memory；撤销后立即退出后续业务输入。</p>
        </div>
        <SafetyCertificateOutlined />
      </aside>
    </template>

    <Modal
      v-model:open="decisionOpen"
      :title="decisionTitle()"
      :ok-text="decisionConfirmText()"
      cancel-text="返回检查"
      :confirm-loading="deciding"
      :ok-button-props="{ danger: decisionAction !== 'confirm' }"
      :mask-closable="!deciding"
      @ok="submitDecision"
      @cancel="closeDecision"
    >
      <div class="decision-dialog">
        <Alert
          v-if="decisionAction === 'confirm' && decisionMemory?.supersedesId"
          type="warning"
          show-icon
          message="确认后，新旧 Memory 将在同一事务中完成替代"
        />
        <div v-if="replacementExisting" class="replacement-preview">
          <span>当前值</span>
          <p>{{ replacementExisting.content }}</p>
          <SwapOutlined />
          <span>候选新值</span>
          <p>{{ decisionMemory?.content }}</p>
        </div>
        <p v-else class="decision-dialog__content">{{ decisionMemory?.content }}</p>
        <label for="decision-note">决策说明（可选）</label>
        <Input.TextArea
          id="decision-note"
          v-model:value="decisionNote"
          :maxlength="500"
          show-count
          :auto-size="{ minRows: 3, maxRows: 6 }"
          placeholder="说明确认、拒绝或撤销原因，不会改变状态机规则"
        />
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.memory-page {
  display: grid;
  gap: 24px;
}

.memory-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 28px;
  padding: 8px 0 4px;
}

.memory-hero > div {
  max-width: 790px;
}

.memory-hero p,
.memory-readiness header p,
.section-heading p {
  margin: 0 0 8px;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 750;
}

.memory-hero h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(27px, 3vw, 38px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}

.memory-hero > div > span {
  display: block;
  margin-top: 12px;
  color: var(--cf-color-text-muted);
}

.memory-loading,
.memory-readiness,
.pending-section,
.confirmed-section,
.memory-principle {
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-panel);
  background: #fff;
}

.memory-loading,
.memory-readiness,
.pending-section,
.confirmed-section {
  padding: 24px;
}

.memory-readiness {
  display: grid;
  gap: 16px;
  border-color: #dbe2ff;
  background: #f7f8ff;
}

.memory-readiness > header,
.section-heading,
.memory-card > header,
.memory-card > footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.memory-readiness h3,
.section-heading h3,
.memory-principle h3 {
  margin: 0;
  color: #0f1d46;
  font-size: 19px;
}

.memory-readiness__value {
  margin: 0;
  color: #172554;
  font-size: 22px;
  font-weight: 700;
}

.memory-readiness__empty {
  margin: 0;
  color: var(--cf-color-text-secondary);
}

.memory-readiness footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  padding-top: 14px;
  border-top: 1px solid #dbe2ff;
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

.memory-readiness footer span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pending-section,
.confirmed-section {
  display: grid;
  gap: 18px;
}

.section-heading {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--cf-color-border);
}

.section-heading > span {
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

.memory-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.memory-list--pending .memory-card {
  border-color: #dbe2ff;
}

.memory-card {
  display: grid;
  align-content: start;
  gap: 14px;
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-card);
  background: #fff;
}

.memory-card > header > span {
  color: var(--cf-color-text-muted);
  font-size: 12px;
}

.memory-card__content {
  margin: 0;
  color: #172554;
  font-size: 15px;
  font-weight: 600;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.memory-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  color: var(--cf-color-text-muted);
  font-size: 11px;
}

.memory-card__meta span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.memory-card > footer {
  align-items: center;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--cf-color-border);
}

.memory-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.memory-filters button {
  padding: 7px 13px;
  border: 1px solid var(--cf-color-border);
  border-radius: 999px;
  background: #fff;
  color: var(--cf-color-text-secondary);
  cursor: pointer;
}

.memory-filters button:hover,
.memory-filters .memory-filter--active {
  border-color: #a5b4fc;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
}

.memory-principle {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 20px 22px;
  background: #f8fafc;
}

.memory-principle > :first-child {
  color: var(--cf-color-warning);
  font-size: 22px;
}

.memory-principle > :last-child {
  color: var(--cf-color-success);
  font-size: 20px;
}

.memory-principle p,
.decision-dialog__content,
.replacement-preview p {
  margin: 5px 0 0;
  color: var(--cf-color-text-muted);
}

.decision-dialog {
  display: grid;
  gap: 16px;
  padding-top: 8px;
}

.decision-dialog label,
.replacement-preview span {
  color: var(--cf-color-text-secondary);
  font-size: 13px;
  font-weight: 650;
}

.decision-dialog__content,
.replacement-preview {
  padding: 14px;
  border-radius: var(--cf-radius-control);
  background: #f8fafc;
  white-space: pre-wrap;
}

.replacement-preview {
  display: grid;
  gap: 8px;
}

.replacement-preview > :nth-child(3) {
  color: var(--cf-color-primary);
}

@media (max-width: 900px) {
  .memory-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .memory-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .memory-readiness > header,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
