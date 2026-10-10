<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  ClockCircleOutlined,
  DatabaseOutlined,
  FileDoneOutlined,
  PlusOutlined,
  ReloadOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Empty, Select, Skeleton, Tag } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import {
  generatePlanForGap,
  loadTrainingGenerationContext,
  loadTrainingPlanHistory,
  type TrainingGenerationContext,
  type TrainingPlanHistoryPage,
} from '@/features/training/api/training-api'
import { ROUTE_PATHS } from '@/router/paths'
import type {
  ListTrainingPlansStatus,
  TrainingPlanResponse,
  TrainingPlanResponseStatus,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

type PlanFilter = 'ALL' | ListTrainingPlansStatus

const router = useRouter()
const context = ref<TrainingGenerationContext | null>(null)
const plans = ref<TrainingPlanResponse[]>([])
const selectedGapId = ref<string>()
const statusFilter = ref<PlanFilter>('ALL')
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const generating = ref(false)
const pageError = ref('')
const generationError = ref('')

const weeklyHoursMemory = computed(() =>
  context.value?.confirmedMemories.find(
    (memory) => memory.type === 'TIME_CONSTRAINT' && memory.normalizedKey === 'weekly_hours',
  ),
)
const gapOptions = computed(() =>
  (context.value?.gapSnapshots ?? []).map((snapshot) => ({
    value: snapshot.snapshotId,
    label: `岗位 v${snapshot.targetRoleVersion ?? '-'} · 画像 v${snapshot.profileVersion ?? '-'} · ${formatTime(snapshot.createdAt)}`,
  })),
)
const canGenerate = computed(() => Boolean(selectedGapId.value && weeklyHoursMemory.value))

const filters: Array<{ value: PlanFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING_CONFIRMATION', label: '待确认' },
  { value: 'ACTIVE', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

onMounted(loadPage)
watch(statusFilter, () => void loadHistory(false))

async function loadPage() {
  loading.value = true
  pageError.value = ''
  generationError.value = ''
  try {
    const [contextResult, historyResult] = await Promise.allSettled([
      loadTrainingGenerationContext(),
      loadTrainingPlanHistory({ limit: 10 }),
    ])
    if (contextResult.status === 'fulfilled') {
      context.value = contextResult.value
      selectedGapId.value = contextResult.value.gapSnapshots[0]?.snapshotId
    } else pageError.value = toApiError(contextResult.reason).message

    if (historyResult.status === 'fulfilled') applyHistory(historyResult.value, false)
    else pageError.value ||= toApiError(historyResult.reason).message
  } finally {
    loading.value = false
  }
}

async function loadHistory(append: boolean) {
  if (append) loadingMore.value = true
  else loading.value = true
  pageError.value = ''
  try {
    const page = await loadTrainingPlanHistory({
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      cursor: append ? (nextCursor.value ?? undefined) : undefined,
      limit: 10,
    })
    applyHistory(page, append)
  } catch (error) {
    pageError.value = toApiError(error).message
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function applyHistory(page: TrainingPlanHistoryPage, append: boolean) {
  plans.value = append
    ? [
        ...plans.value,
        ...page.items.filter((next) => !plans.value.some((item) => item.planId === next.planId)),
      ]
    : page.items
  nextCursor.value = page.nextCursor
  hasMore.value = page.hasMore
}

async function generatePlan() {
  if (!selectedGapId.value || !canGenerate.value) return
  generating.value = true
  generationError.value = ''
  try {
    const plan = await generatePlanForGap(selectedGapId.value)
    await router.push(`${ROUTE_PATHS.trainingPlans}/${plan.planId}`)
  } catch (error) {
    generationError.value = toApiError(error).message
  } finally {
    generating.value = false
  }
}

function statusMeta(status?: TrainingPlanResponseStatus) {
  const map: Record<TrainingPlanResponseStatus, { label: string; color: string }> = {
    DRAFT: { label: '生成中草案', color: 'default' },
    PENDING_CONFIRMATION: { label: '待确认', color: 'gold' },
    ACTIVE: { label: '进行中', color: 'blue' },
    COMPLETED: { label: '已完成', color: 'green' },
    CANCELLED: { label: '已取消', color: 'red' },
  }
  return status ? map[status] : { label: '状态待确认', color: 'default' }
}

function progress(plan: TrainingPlanResponse) {
  const items = plan.items ?? []
  if (!items.length) return 0
  return Math.round(
    (items.filter((item) => item.status === 'COMPLETED').length / items.length) * 100,
  )
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
  <div class="training-page">
    <header class="training-hero">
      <div>
        <p>个性化成长训练</p>
        <h2>把能力差距，变成每周可执行的任务</h2>
        <span>计划由模型生成候选，经 Java 校验后等待你确认；状态和进度始终来自 MySQL。</span>
      </div>
      <Button :loading="loading" @click="loadPage"><ReloadOutlined />刷新</Button>
    </header>

    <Alert v-if="pageError" type="error" show-icon :message="pageError" />
    <section v-if="loading && !context" class="training-loading">
      <Skeleton active :paragraph="{ rows: 8 }" />
    </section>

    <template v-else>
      <section class="plan-generator">
        <header>
          <div>
            <p>生成新计划</p>
            <h3>固定输入检查</h3>
          </div>
          <Tag :color="canGenerate ? 'green' : 'orange'">{{
            canGenerate ? '可以生成' : '前置条件不足'
          }}</Tag>
        </header>
        <div class="generator-inputs">
          <article>
            <FileDoneOutlined />
            <div>
              <span>能力差距快照</span><strong>{{ gapOptions.length }} 份可选</strong>
            </div>
          </article>
          <article>
            <DatabaseOutlined />
            <div>
              <span>每周学习时间</span
              ><strong>{{ weeklyHoursMemory?.content || '尚未确认' }}</strong>
            </div>
          </article>
        </div>
        <Alert v-if="generationError" type="error" show-icon :message="generationError" />
        <div v-if="!gapOptions.length" class="generator-missing">
          <span>请先生成能力差距快照。</span
          ><Button @click="router.push(ROUTE_PATHS.skillGaps)">前往能力差距</Button>
        </div>
        <div v-else-if="!weeklyHoursMemory" class="generator-missing">
          <span>请先确认每周可用学习时间。</span
          ><Button @click="router.push(ROUTE_PATHS.memories)">前往长期记忆</Button>
        </div>
        <div v-else class="generator-action">
          <Select
            v-model:value="selectedGapId"
            :options="gapOptions"
            aria-label="选择能力差距快照"
          />
          <Button type="primary" size="large" :loading="generating" @click="generatePlan"
            ><PlusOutlined />{{
              generating ? '正在生成并校验，最长约 90 秒' : '生成待确认训练计划'
            }}</Button
          >
        </div>
      </section>

      <section class="plan-history">
        <header class="section-heading">
          <div>
            <p>不可变业务版本</p>
            <h3>训练计划历史</h3>
          </div>
          <span>{{ plans.length }} 份</span>
        </header>
        <div class="plan-filters">
          <button
            v-for="filter in filters"
            :key="filter.value"
            type="button"
            :class="{ active: statusFilter === filter.value }"
            @click="statusFilter = filter.value"
          >
            {{ filter.label }}
          </button>
        </div>
        <Empty v-if="!plans.length" description="当前筛选条件下没有训练计划" />
        <div v-else class="plan-list">
          <button
            v-for="plan in plans"
            :key="plan.planId"
            type="button"
            class="plan-card"
            @click="router.push(`${ROUTE_PATHS.trainingPlans}/${plan.planId}`)"
          >
            <header>
              <Tag :color="statusMeta(plan.status).color">{{ statusMeta(plan.status).label }}</Tag
              ><span>计划 v{{ plan.planVersion ?? '-' }}</span>
            </header>
            <h4>{{ plan.title || '未命名训练计划' }}</h4>
            <p>
              {{ plan.durationWeeks ?? '-' }} 周 · {{ plan.items?.length ?? 0 }} 项任务 · 完成
              {{ progress(plan) }}%
            </p>
            <footer>
              <span><ClockCircleOutlined />{{ formatTime(plan.updatedAt) }}</span
              ><RightOutlined />
            </footer>
          </button>
        </div>
        <Button v-if="hasMore" block :loading="loadingMore" @click="loadHistory(true)"
          >加载更早计划</Button
        >
      </section>

      <aside class="training-principle">
        <SafetyCertificateOutlined />
        <div>
          <h3>模型不能激活计划，也不能完成任务</h3>
          <p>所有确认、证据提交和终态操作都由用户发起并携带服务端版本。</p>
        </div>
      </aside>
    </template>
  </div>
</template>

<style scoped>
.training-page {
  display: grid;
  gap: 24px;
}
.training-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 28px;
  padding: 8px 0 4px;
}
.training-hero > div {
  max-width: 790px;
}
.training-hero p,
.plan-generator header p,
.section-heading p {
  margin: 0 0 8px;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 750;
}
.training-hero h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(27px, 3vw, 38px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}
.training-hero > div > span {
  display: block;
  margin-top: 12px;
  color: var(--cf-color-text-muted);
}
.training-loading,
.plan-generator,
.plan-history,
.training-principle {
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-panel);
  background: #fff;
}
.training-loading,
.plan-generator,
.plan-history {
  padding: 24px;
}
.plan-generator,
.plan-history {
  display: grid;
  gap: 18px;
}
.plan-generator > header,
.section-heading,
.plan-card header,
.plan-card footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.plan-generator h3,
.section-heading h3,
.training-principle h3 {
  margin: 0;
  color: #0f1d46;
  font-size: 19px;
}
.generator-inputs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.generator-inputs article {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe2ff;
  border-radius: var(--cf-radius-card);
  background: #f7f8ff;
}
.generator-inputs article > :first-child {
  color: var(--cf-color-primary);
  font-size: 24px;
}
.generator-inputs div {
  display: grid;
  gap: 3px;
}
.generator-inputs span,
.generator-missing span,
.plan-card p,
.plan-card header span,
.training-principle p {
  color: var(--cf-color-text-muted);
  font-size: 13px;
}
.generator-inputs strong {
  color: #172554;
}
.generator-action,
.generator-missing {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
}
.generator-missing {
  padding: 16px;
  border-radius: var(--cf-radius-card);
  background: #fffaf5;
}
.section-heading {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--cf-color-border);
}
.section-heading > span {
  color: var(--cf-color-text-muted);
  font-size: 13px;
}
.plan-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.plan-filters button {
  padding: 7px 13px;
  border: 1px solid var(--cf-color-border);
  border-radius: 999px;
  background: #fff;
  color: var(--cf-color-text-secondary);
  cursor: pointer;
}
.plan-filters button:hover,
.plan-filters .active {
  border-color: #a5b4fc;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
}
.plan-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.plan-card {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-card);
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.plan-card:hover {
  border-color: #a5b4fc;
  box-shadow: var(--cf-shadow-card);
}
.plan-card h4 {
  margin: 0;
  color: #172554;
  font-size: 17px;
}
.plan-card p {
  margin: 0;
}
.plan-card footer {
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--cf-color-border);
  color: var(--cf-color-primary);
}
.plan-card footer span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}
.training-principle {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 22px;
  background: #f8fafc;
}
.training-principle > :first-child {
  margin-top: 3px;
  color: var(--cf-color-success);
  font-size: 22px;
}
.training-principle p {
  margin: 5px 0 0;
}
@media (max-width: 760px) {
  .training-hero {
    align-items: stretch;
    flex-direction: column;
  }
  .generator-inputs,
  .plan-list {
    grid-template-columns: minmax(0, 1fr);
  }
  .generator-action,
  .generator-missing {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
