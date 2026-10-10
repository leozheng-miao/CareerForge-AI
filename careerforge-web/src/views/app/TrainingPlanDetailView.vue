<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowLeftOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Empty, Input, Modal, Progress, Skeleton, Tag } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

import {
  loadTrainingPlan,
  mutateReviewedTrainingPlan,
  normalizeEvidenceRefs,
  TrainingPlanChangedError,
  type TrainingPlanAction,
} from '@/features/training/api/training-api'
import { ROUTE_PATHS } from '@/router/paths'
import type {
  TrainingPlanItem,
  TrainingPlanResponse,
  TrainingPlanResponseStatus,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

const route = useRoute(),
  router = useRouter()
const plan = ref<TrainingPlanResponse | null>(null),
  loading = ref(true),
  mutating = ref(false)
const pageError = ref(''),
  operationError = ref(''),
  operationSuccess = ref('')
const modalOpen = ref(false),
  pendingAction = ref<TrainingPlanAction>('confirm'),
  pendingItem = ref<TrainingPlanItem | null>(null),
  evidenceText = ref('')
const planId = computed(() => String(route.params.planId ?? ''))
const completedCount = computed(
  () => plan.value?.items?.filter((i) => i.status === 'COMPLETED').length ?? 0,
)
const totalCount = computed(() => plan.value?.items?.length ?? 0)
const progress = computed(() =>
  totalCount.value ? Math.round((completedCount.value / totalCount.value) * 100) : 0,
)
const allCompleted = computed(
  () => totalCount.value > 0 && completedCount.value === totalCount.value,
)

onMounted(refreshPlan)
async function refreshPlan() {
  loading.value = true
  pageError.value = ''
  try {
    plan.value = await loadTrainingPlan(planId.value)
  } catch (e) {
    pageError.value = toApiError(e).message
  } finally {
    loading.value = false
  }
}
function openAction(action: TrainingPlanAction, item: TrainingPlanItem | null = null) {
  pendingAction.value = action
  pendingItem.value = item
  evidenceText.value = ''
  operationError.value = ''
  operationSuccess.value = ''
  modalOpen.value = true
}
function closeModal() {
  if (!mutating.value) modalOpen.value = false
}
async function submitAction() {
  if (!plan.value) return
  mutating.value = true
  operationError.value = ''
  try {
    const evidenceRefs =
      pendingAction.value === 'complete-item'
        ? normalizeEvidenceRefs(evidenceText.value.split(/[\n,]+/))
        : undefined
    plan.value = await mutateReviewedTrainingPlan(pendingAction.value, plan.value, {
      itemId: pendingItem.value?.itemId,
      evidenceRefs,
    })
    operationSuccess.value = successMessage(pendingAction.value)
    modalOpen.value = false
  } catch (e) {
    if (e instanceof TrainingPlanChangedError) {
      plan.value = e.latestPlan
      operationError.value = e.message
      modalOpen.value = false
    } else operationError.value = toApiError(e).message
  } finally {
    mutating.value = false
  }
}
async function startItem(item: TrainingPlanItem) {
  if (!plan.value) return
  mutating.value = true
  operationError.value = ''
  try {
    plan.value = await mutateReviewedTrainingPlan('start-item', plan.value, { itemId: item.itemId })
    operationSuccess.value = '训练项已开始。'
  } catch (e) {
    if (e instanceof TrainingPlanChangedError) {
      plan.value = e.latestPlan
      operationError.value = e.message
    } else operationError.value = toApiError(e).message
  } finally {
    mutating.value = false
  }
}
function successMessage(action: TrainingPlanAction) {
  return {
    confirm: '计划已确认并激活。',
    complete: '训练计划已完成。',
    cancel: '训练计划已取消。',
    'complete-item': '训练项已完成并保存证据。',
    'start-item': '训练项已开始。',
  }[action]
}
function actionTitle() {
  if (pendingAction.value === 'confirm') return '确认并激活训练计划？'
  if (pendingAction.value === 'cancel') return '取消训练计划？'
  if (pendingAction.value === 'complete') return '确认完成整份计划？'
  return '提交完成证据'
}
function statusMeta(status?: TrainingPlanResponseStatus) {
  const map: Record<TrainingPlanResponseStatus, { label: string; color: string }> = {
    DRAFT: { label: '草案', color: 'default' },
    PENDING_CONFIRMATION: { label: '待确认', color: 'gold' },
    ACTIVE: { label: '进行中', color: 'blue' },
    COMPLETED: { label: '已完成', color: 'green' },
    CANCELLED: { label: '已取消', color: 'red' },
  }
  return status ? map[status] : { label: '未知', color: 'default' }
}
function itemStatus(item: TrainingPlanItem) {
  return item.status === 'COMPLETED'
    ? { label: '已完成', color: 'green' }
    : item.status === 'IN_PROGRESS'
      ? { label: '进行中', color: 'blue' }
      : { label: '未开始', color: 'default' }
}
function formatTime(v?: string) {
  if (!v) return '暂无时间'
  const d = new Date(v)
  return Number.isNaN(d.getTime())
    ? '时间待确认'
    : new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).format(d)
}
</script>

<template>
  <div class="plan-detail-page">
    <header class="detail-nav">
      <Button @click="router.push(ROUTE_PATHS.trainingPlans)"
        ><ArrowLeftOutlined />返回计划列表</Button
      ><Button :loading="loading" @click="refreshPlan">刷新服务端状态</Button>
    </header>
    <Alert v-if="pageError" type="error" show-icon :message="pageError" /><Alert
      v-if="operationError"
      type="error"
      show-icon
      :message="operationError"
    /><Alert
      v-if="operationSuccess"
      type="success"
      show-icon
      :message="operationSuccess"
      closable
    />
    <section v-if="loading && !plan" class="detail-loading">
      <Skeleton active :paragraph="{ rows: 9 }" />
    </section>
    <template v-else-if="plan">
      <section class="plan-summary">
        <header>
          <div>
            <p>训练计划 v{{ plan.planVersion ?? '-' }}</p>
            <h2>{{ plan.title || '未命名训练计划' }}</h2>
            <span
              >来源 Gap {{ plan.gapSnapshotId || '-' }} · 乐观锁版本 {{ plan.version ?? '-' }}</span
            >
          </div>
          <Tag :color="statusMeta(plan.status).color">{{ statusMeta(plan.status).label }}</Tag>
        </header>
        <Progress :percent="progress" stroke-color="#4f46e5" />
        <footer>
          <span>{{ plan.durationWeeks ?? '-' }} 周</span
          ><span>{{ completedCount }} / {{ totalCount }} 项完成</span
          ><span><ClockCircleOutlined />{{ formatTime(plan.updatedAt) }}</span>
        </footer>
        <div class="plan-actions">
          <Button
            v-if="plan.status === 'PENDING_CONFIRMATION'"
            type="primary"
            @click="openAction('confirm')"
            ><CheckCircleFilled />确认并激活</Button
          ><Button
            v-if="plan.status === 'ACTIVE'"
            type="primary"
            :disabled="!allCompleted"
            @click="openAction('complete')"
            >完成整份计划</Button
          ><Button
            v-if="plan.status === 'PENDING_CONFIRMATION' || plan.status === 'ACTIVE'"
            danger
            @click="openAction('cancel')"
            ><StopOutlined />取消计划</Button
          >
        </div>
      </section>
      <section class="plan-items">
        <header>
          <div>
            <p>按周执行</p>
            <h3>训练任务</h3>
          </div>
          <span>{{ totalCount }} 项</span>
        </header>
        <Empty v-if="!totalCount" description="计划没有训练项" />
        <article v-for="item in plan.items" :key="item.itemId" class="plan-item">
          <header>
            <div>
              <Tag>第 {{ item.weekNumber ?? '-' }} 周</Tag
              ><Tag :color="itemStatus(item).color">{{ itemStatus(item).label }}</Tag>
            </div>
            <span>预计 {{ item.estimatedMinutes ?? '-' }} 分钟</span>
          </header>
          <h4>{{ item.title || '未命名任务' }}</h4>
          <p>{{ item.taskDescription }}</p>
          <dl>
            <div>
              <dt>完成标准</dt>
              <dd>{{ item.completionCriteria }}</dd>
            </div>
            <div>
              <dt>证据要求</dt>
              <dd>{{ item.evidenceRequirement }}</dd>
            </div>
          </dl>
          <div class="item-meta">
            <span>关联 Gap {{ item.gapItemIds?.length ?? 0 }} 项</span
            ><span>受控资源 {{ item.resourceRefs?.length ?? 0 }} 项</span>
          </div>
          <div v-if="item.completionEvidenceRefs?.length" class="evidence-list">
            <strong>完成证据</strong
            ><code v-for="refValue in item.completionEvidenceRefs" :key="refValue">{{
              refValue
            }}</code>
          </div>
          <footer v-if="plan.status === 'ACTIVE'">
            <Button
              v-if="item.status === 'NOT_STARTED'"
              :loading="mutating"
              @click="startItem(item)"
              ><PlayCircleOutlined />开始任务</Button
            ><Button
              v-if="item.status === 'IN_PROGRESS'"
              type="primary"
              @click="openAction('complete-item', item)"
              ><CheckCircleFilled />提交证据并完成</Button
            >
          </footer>
        </article>
      </section>
      <aside class="detail-principle">
        <SafetyCertificateOutlined />
        <div>
          <h3>完成证据只保存受控引用</h3>
          <p>当前后端仅接受 `github:` 或 `test-report:` 前缀；页面不会上传或伪造证据内容。</p>
        </div>
      </aside>
    </template>
    <Modal
      v-model:open="modalOpen"
      :title="actionTitle()"
      :ok-text="pendingAction === 'complete-item' ? '提交并完成' : '确认操作'"
      cancel-text="返回检查"
      :confirm-loading="mutating"
      :ok-button-props="{ danger: pendingAction === 'cancel' }"
      @ok="submitAction"
      @cancel="closeModal"
      ><div class="action-modal">
        <Alert
          v-if="pendingAction === 'cancel'"
          type="warning"
          show-icon
          message="取消后计划进入终态，已有任务进度仍保留用于审计"
        /><template v-if="pendingAction === 'complete-item'"
          ><p>{{ pendingItem?.evidenceRequirement }}</p>
          <label for="evidence-refs">完成证据引用</label
          ><Input.TextArea
            id="evidence-refs"
            v-model:value="evidenceText"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            placeholder="每行一条，例如：&#10;github:careerforge/pull/123&#10;test-report:training/item-1"
          /><small>支持 1–20 条，以换行或逗号分隔。</small></template
        >
        <p v-else>操作前会重新读取服务端计划版本；状态变化时不会继续提交。</p>
      </div></Modal
    >
  </div>
</template>

<style scoped>
.plan-detail-page {
  display: grid;
  gap: 20px;
}
.detail-nav {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.detail-loading,
.plan-summary,
.plan-items,
.detail-principle {
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-panel);
  background: #fff;
}
.detail-loading,
.plan-summary,
.plan-items {
  padding: 24px;
}
.plan-summary {
  display: grid;
  gap: 20px;
}
.plan-summary > header,
.plan-summary footer,
.plan-items > header,
.plan-item > header,
.plan-item > footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.plan-summary p,
.plan-items > header p {
  margin: 0 0 7px;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 750;
}
.plan-summary h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(26px, 3vw, 36px);
}
.plan-summary header span,
.plan-summary footer,
.plan-items > header > span,
.plan-item header > span,
.item-meta {
  color: var(--cf-color-text-muted);
  font-size: 12px;
}
.plan-summary footer {
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 12px 24px;
}
.plan-summary footer span {
  display: flex;
  align-items: center;
  gap: 5px;
}
.plan-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}
.plan-items {
  display: grid;
  gap: 16px;
}
.plan-items h3 {
  margin: 0;
  color: #0f1d46;
  font-size: 20px;
}
.plan-item {
  display: grid;
  gap: 13px;
  padding: 20px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-card);
}
.plan-item h4 {
  margin: 0;
  color: #172554;
  font-size: 18px;
}
.plan-item > p {
  margin: 0;
  color: var(--cf-color-text-secondary);
  white-space: pre-wrap;
}
.plan-item dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}
.plan-item dl div {
  padding: 14px;
  border-radius: var(--cf-radius-control);
  background: #f8fafc;
}
.plan-item dt {
  color: var(--cf-color-text-muted);
  font-size: 12px;
  font-weight: 700;
}
.plan-item dd {
  margin: 5px 0 0;
  color: var(--cf-color-text-secondary);
  font-size: 13px;
}
.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.evidence-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.evidence-list code {
  padding: 4px 7px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #334155;
}
.plan-item > footer {
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--cf-color-border);
}
.detail-principle {
  display: flex;
  gap: 14px;
  padding: 20px 22px;
  background: #f8fafc;
}
.detail-principle > :first-child {
  color: var(--cf-color-success);
  font-size: 22px;
}
.detail-principle h3 {
  margin: 0;
  color: #0f1d46;
  font-size: 18px;
}
.detail-principle p,
.action-modal p,
.action-modal small {
  margin: 5px 0 0;
  color: var(--cf-color-text-muted);
}
.action-modal {
  display: grid;
  gap: 14px;
}
.action-modal label {
  color: var(--cf-color-text-secondary);
  font-weight: 650;
}
@media (max-width: 700px) {
  .plan-item dl {
    grid-template-columns: minmax(0, 1fr);
  }
  .detail-nav,
  .plan-summary > header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
