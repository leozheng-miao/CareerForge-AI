<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRightOutlined,
  BulbOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  DatabaseOutlined,
  FileDoneOutlined,
  MessageOutlined,
  ReadOutlined,
  ReloadOutlined,
  RobotOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Progress, Skeleton, Tag } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import {
  loadDashboardSnapshot,
  type DashboardSection,
  type DashboardSnapshot,
} from '@/features/dashboard/api/dashboard-api'
import { useAuthStore } from '@/features/auth/store/auth-store'
import { ROUTE_PATHS } from '@/router/paths'
import type {
  CoachingSessionResponse,
  GapItem,
  TargetRoleResponse,
  TrainingPlanResponse,
} from '@/shared/api/generated/model'
import type { InterviewRecord } from '@/features/interview/api/interview-flow-api'

const authStore = useAuthStore()
const router = useRouter()
const snapshot = ref<DashboardSnapshot | null>(null)
const loading = ref(true)
const pageError = ref('')

const displayName = computed(() => authStore.currentUser?.displayName || '你好')
const targetRole = computed(() => snapshot.value?.targetRole.data ?? null)
const skillGap = computed(() => snapshot.value?.skillGap.data ?? null)
const trainingPlan = computed(() => snapshot.value?.trainingPlan.data ?? null)
const pendingMemoryCount = computed(() => snapshot.value?.pendingMemories.data.length ?? 0)
const coachingSession = computed(() => snapshot.value?.coachingSession.data ?? null)
const mockInterview = computed(() => snapshot.value?.mockInterview.data ?? null)

const gapCounts = computed(() => {
  const items = skillGap.value?.items ?? []
  return {
    total: items.length,
    matched: countGap(items, 'MATCHED'),
    partial: countGap(items, 'PARTIAL'),
    missing: countGap(items, 'MISSING'),
    unverified: countGap(items, 'UNVERIFIED'),
  }
})

const trainingProgress = computed(() => {
  const items = trainingPlan.value?.items ?? []
  if (items.length === 0) return 0
  const completed = items.filter((item) => item.status === 'COMPLETED').length
  return Math.round((completed / items.length) * 100)
})

const nextTrainingItem = computed(() =>
  trainingPlan.value?.items?.find((item) => item.status !== 'COMPLETED'),
)

const failedSections = computed(() => {
  if (!snapshot.value) return []
  return [
    ['目标岗位', snapshot.value.targetRole],
    ['能力差距', snapshot.value.skillGap],
    ['训练计划', snapshot.value.trainingPlan],
    ['长期记忆', snapshot.value.pendingMemories],
    ['Career Coach', snapshot.value.coachingSession],
    ['模拟面试', snapshot.value.mockInterview],
  ].flatMap(([label, section]) => {
    const error = (section as DashboardSection<unknown>).error
    return error ? [`${label}：${error}`] : []
  })
})

const nextAction = computed(() => {
  if (!snapshot.value || failedSections.value.length > 0) {
    return {
      eyebrow: '先恢复最新状态',
      title: '部分数据暂时未能加载',
      description: '重新读取 REST 快照后，再决定下一步操作。',
      label: '重新加载总览',
      action: reload,
    }
  }
  if (!targetRole.value) {
    return {
      eyebrow: '第一步 · 定目标',
      title: '从一份真实 JD 开始职业诊断',
      description: '创建并确认目标岗位后，系统才能基于同一版本生成能力差距。',
      label: '开始建立目标岗位',
      action: openTargetRole,
    }
  }
  if (!skillGap.value) {
    return {
      eyebrow: '第二步 · 找差距',
      title: '为当前目标岗位生成能力差距',
      description: '关联已确认的技能画像与证据，形成可追溯的差距快照。',
      label: '生成能力差距',
      action: openSkillGaps,
    }
  }
  if (pendingMemoryCount.value > 0) {
    return {
      eyebrow: '需要你确认',
      title: `有 ${pendingMemoryCount.value} 条长期记忆候选等待决定`,
      description: '模型只能提出候选，是否进入长期画像必须由你确认。',
      label: '审阅 Memory 候选',
      action: openMemories,
    }
  }
  if (!trainingPlan.value) {
    return {
      eyebrow: '第三步 · 做训练',
      title: '把能力差距转化为可执行训练计划',
      description: '计划会引用已确认的差距与长期约束，不在前端自行生成。',
      label: '生成训练计划',
      action: openTrainingPlans,
    }
  }
  return {
    eyebrow: '继续当前成长路径',
    title: nextTrainingItem.value?.title || trainingPlan.value.title || '继续训练与复盘',
    description: nextTrainingItem.value?.taskDescription || '完成当前计划后，用模拟面试验证能力。',
    label: `当前计划进度 ${trainingProgress.value}%`,
    action: openTrainingPlans,
  }
})

onMounted(reload)

function openTargetRole() {
  void router.push(ROUTE_PATHS.targetRole)
}

function openSkillGaps() {
  void router.push(ROUTE_PATHS.skillGaps)
}

function openMemories() {
  void router.push(ROUTE_PATHS.memories)
}

function openTrainingPlans() {
  void router.push(ROUTE_PATHS.trainingPlans)
}

async function reload() {
  loading.value = true
  pageError.value = ''
  try {
    snapshot.value = await loadDashboardSnapshot()
  } catch {
    pageError.value = '成长总览暂时无法加载，请稍后重试'
  } finally {
    loading.value = false
  }
}

function countGap(items: GapItem[], status: GapItem['status']) {
  return items.filter((item) => item.status === status).length
}

function formatTime(value?: string) {
  if (!value) return '暂无更新时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间待确认'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function roleRequirementCount(role: TargetRoleResponse | null) {
  if (!role?.requirements) return 0
  const requirements = role.requirements
  return [
    requirements.programmingLanguages,
    requirements.backendAndInfrastructureRequirements,
    requirements.agentRequirements,
    requirements.ragRequirements,
    requirements.engineeringRequirements,
    requirements.bonusQualifications,
  ].reduce((total, items) => total + (items?.length ?? 0), 0)
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_CONFIRMATION: '待确认',
    ACTIVE: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    CREATED: '已创建',
    PREPARING: '准备中',
    READY: '待开始',
    INTERVIEWING: '面试中',
    GENERATING_QUESTION: '生成问题中',
    WAITING_FOR_ANSWER: '等待回答',
    REVIEWING: '评审中',
    GENERATING_REPORT: '生成报告中',
    AWAITING_CONFIRMATION: '等待确认',
    FAILED: '失败',
    INTERRUPTED: '已中断',
    CLOSED: '已关闭',
  }
  return status ? (labels[status] ?? status) : '暂无状态'
}

function interviewSummary(interview: InterviewRecord | null) {
  if (!interview) return '还没有模拟面试记录'
  return 'AI Agent 开发快速面试'
}

function coachingSummary(session: CoachingSessionResponse | null) {
  if (!session) return '还没有 Career Coach 会话'
  return session.title || '未命名会话'
}

function planSummary(plan: TrainingPlanResponse | null) {
  if (!plan) return '还没有训练计划'
  return plan.title || `第 ${plan.planVersion ?? '-'} 版训练计划`
}
</script>

<template>
  <div class="dashboard-page">
    <header class="dashboard-hero">
      <div>
        <p class="dashboard-hero__eyebrow">今天的成长起点</p>
        <h2>{{ displayName }}，先确认当前状态，再开始下一步</h2>
        <p>总览只展示后端最新事实；数据变化时重新读取，不在浏览器推演业务状态。</p>
      </div>
      <Button :loading="loading" @click="reload"><ReloadOutlined />刷新状态</Button>
    </header>

    <Alert v-if="pageError" type="error" show-icon :message="pageError">
      <template #action><Button size="small" @click="reload">重试</Button></template>
    </Alert>

    <template v-if="loading && !snapshot">
      <section class="dashboard-loading" aria-label="正在加载成长总览">
        <Skeleton active :paragraph="{ rows: 3 }" />
        <Skeleton active :paragraph="{ rows: 6 }" />
      </section>
    </template>

    <template v-else-if="snapshot">
      <Alert
        v-if="failedSections.length"
        class="dashboard-warning"
        type="warning"
        show-icon
        message="部分模块加载失败，其余数据仍可查看"
        :description="failedSections.join('；')"
      />

      <section class="next-action" aria-labelledby="next-action-title">
        <div class="next-action__icon"><BulbOutlined /></div>
        <div>
          <p>{{ nextAction.eyebrow }}</p>
          <h2 id="next-action-title">{{ nextAction.title }}</h2>
          <span>{{ nextAction.description }}</span>
        </div>
        <Button type="primary" size="large" @click="nextAction.action()">
          {{ nextAction.label }}
          <ArrowRightOutlined />
        </Button>
      </section>

      <section class="dashboard-grid" aria-label="成长状态概览">
        <article class="dashboard-card dashboard-card--wide">
          <header>
            <span><ReadOutlined />目标岗位</span><Tag color="blue">已确认版本</Tag>
          </header>
          <template v-if="targetRole">
            <h3>{{ targetRole.requirements?.jobTitle || '目标岗位名称待补充' }}</h3>
            <p>已识别 {{ roleRequirementCount(targetRole) }} 条技术与工程要求</p>
            <footer>
              <span>版本 {{ targetRole.targetRoleVersion ?? '-' }}</span>
              <span><ClockCircleOutlined />{{ formatTime(targetRole.confirmedAt) }}</span>
            </footer>
          </template>
          <p v-else class="dashboard-card__empty">尚未确认目标岗位。下一步从真实 JD 建立目标。</p>
        </article>

        <article class="dashboard-card">
          <header>
            <span><BulbOutlined />能力差距</span><span>{{ gapCounts.total }} 项</span>
          </header>
          <template v-if="skillGap">
            <div class="gap-summary">
              <span
                ><strong>{{ gapCounts.matched }}</strong
                >已匹配</span
              >
              <span
                ><strong>{{ gapCounts.partial }}</strong
                >部分匹配</span
              >
              <span
                ><strong>{{ gapCounts.missing }}</strong
                >待补齐</span
              >
              <span
                ><strong>{{ gapCounts.unverified }}</strong
                >待验证</span
              >
            </div>
            <p class="dashboard-card__meta">快照生成于 {{ formatTime(skillGap.createdAt) }}</p>
          </template>
          <p v-else class="dashboard-card__empty">尚无能力差距快照。</p>
        </article>

        <article class="dashboard-card">
          <header>
            <span><FileDoneOutlined />训练计划</span
            ><Tag>{{ statusLabel(trainingPlan?.status) }}</Tag>
          </header>
          <h3>{{ planSummary(trainingPlan) }}</h3>
          <template v-if="trainingPlan">
            <Progress :percent="trainingProgress" :show-info="true" stroke-color="#4f46e5" />
            <p class="dashboard-card__meta">
              {{
                nextTrainingItem ? `下一项：${nextTrainingItem.title}` : '当前计划没有待完成训练项'
              }}
            </p>
          </template>
        </article>

        <article class="dashboard-card">
          <header>
            <span><DatabaseOutlined />长期记忆</span><Tag color="gold">需用户确认</Tag>
          </header>
          <div class="dashboard-card__metric">{{ pendingMemoryCount }}</div>
          <p>条候选等待决定。总览不展示内容，避免无意暴露个人信息。</p>
        </article>

        <article class="dashboard-card">
          <header>
            <span><MessageOutlined />Career Coach</span
            ><Tag>{{ statusLabel(coachingSession?.status) }}</Tag>
          </header>
          <h3>{{ coachingSummary(coachingSession) }}</h3>
          <p class="dashboard-card__meta">
            <ClockCircleOutlined />{{ formatTime(coachingSession?.updatedAt) }}
          </p>
        </article>

        <article class="dashboard-card">
          <header>
            <span><RobotOutlined />模拟面试</span
            ><Tag>{{ statusLabel(mockInterview?.stage) }}</Tag>
          </header>
          <h3>{{ interviewSummary(mockInterview) }}</h3>
          <p class="dashboard-card__meta">
            <ClockCircleOutlined />{{ formatTime(mockInterview?.createdAt) }}
          </p>
        </article>

        <article class="dashboard-card dashboard-card--principle">
          <WarningOutlined />
          <div>
            <h3>状态不一致时，以 REST 快照为准</h3>
            <p>SSE 只展示安全进度；训练结果、Coach 回答和面试报告最终都从 MySQL 对应接口读取。</p>
          </div>
          <CheckCircleFilled />
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 24px;
}

.dashboard-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 32px;
  padding: 8px 0 10px;
}

.dashboard-hero__eyebrow,
.next-action p {
  margin: 0 0 8px;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 750;
}

.dashboard-hero h2,
.next-action h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(27px, 3vw, 38px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}

.dashboard-hero > div > p:last-child {
  margin: 12px 0 0;
  color: var(--cf-color-text-muted);
}

.dashboard-loading {
  display: grid;
  gap: 24px;
  padding: 28px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-panel);
  background: #fff;
}

.dashboard-warning {
  margin-bottom: 0;
}

.next-action {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 22px;
  padding: 26px 28px;
  border: 1px solid #d7defc;
  border-radius: var(--cf-radius-panel);
  background: #f7f8ff;
}

.next-action__icon {
  display: flex;
  width: 52px;
  height: 52px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  color: var(--cf-color-primary);
  font-size: 24px;
}

.next-action h2 {
  font-size: 24px;
}

.next-action span {
  display: block;
  margin-top: 7px;
  color: var(--cf-color-text-muted);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.dashboard-card {
  min-width: 0;
  padding: 22px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-card);
  background: #fff;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.02);
}

.dashboard-card--wide {
  grid-column: span 2;
}

.dashboard-card header {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

.dashboard-card header > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #1e293b;
  font-size: 15px;
  font-weight: 750;
}

.dashboard-card header > span:first-child :deep(svg) {
  color: var(--cf-color-primary);
}

.dashboard-card h3 {
  margin: 26px 0 8px;
  overflow: hidden;
  color: #101b3b;
  font-size: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-card > p {
  margin: 0;
  color: var(--cf-color-text-muted);
  line-height: 1.65;
}

.dashboard-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 26px;
  padding-top: 16px;
  border-top: 1px solid #edf1f6;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}

.dashboard-card footer span:last-child,
.dashboard-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dashboard-card__empty {
  margin-top: 26px !important;
  padding: 18px;
  border-radius: 8px;
  background: #f8fafc;
}

.gap-summary {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-top: 22px;
}

.gap-summary span {
  display: grid;
  gap: 2px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}

.gap-summary strong {
  color: #18223f;
  font-size: 20px;
}

.dashboard-card__meta {
  margin-top: 16px !important;
  font-size: 12px;
}

.dashboard-card__metric {
  margin: 20px 0 2px;
  color: #0b1740;
  font-size: 38px;
  font-weight: 750;
}

.dashboard-card--principle {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 16px;
  border-color: #f2dfb5;
  background: #fffcf5;
}

.dashboard-card--principle > :deep(svg) {
  color: var(--cf-color-warning);
  font-size: 24px;
}

.dashboard-card--principle > :deep(svg):last-child {
  color: var(--cf-color-success);
}

.dashboard-card--principle h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.dashboard-card--principle p {
  margin: 0;
  color: var(--cf-color-text-muted);
}

@media (max-width: 1100px) {
  .dashboard-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .dashboard-hero,
  .next-action {
    align-items: stretch;
    flex-direction: column;
  }

  .dashboard-hero {
    display: flex;
  }

  .next-action {
    display: flex;
    padding: 22px;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-card--wide,
  .dashboard-card--principle {
    grid-column: auto;
  }

  .dashboard-card--principle {
    grid-template-columns: auto 1fr;
  }

  .dashboard-card--principle > :deep(svg):last-child {
    display: none;
  }
}
</style>
