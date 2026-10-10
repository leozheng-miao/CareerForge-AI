<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowLeftOutlined,
  BulbOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Empty, Skeleton, Tag } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import {
  generateSkillGapFromReviewedInputs,
  loadSkillGapHistory,
  loadSkillGapInputs,
  SkillGapInputsChangedError,
  type SkillGapHistoryPage,
  type SkillGapInputs,
} from '@/features/skill-gap/api/skill-gap-api'
import { ROUTE_PATHS } from '@/router/paths'
import type { GapItem, GapItemStatus, SkillGapSnapshotResponse } from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

type GapFilter = 'ALL' | GapItemStatus

const router = useRouter()
const inputs = ref<SkillGapInputs | null>(null)
const snapshots = ref<SkillGapSnapshotResponse[]>([])
const selectedSnapshot = ref<SkillGapSnapshotResponse | null>(null)
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const activeFilter = ref<GapFilter>('ALL')
const loading = ref(true)
const loadingMore = ref(false)
const generating = ref(false)
const pageError = ref('')
const historyError = ref('')
const operationError = ref('')
const operationSuccess = ref('')

const targetRole = computed(() => inputs.value?.targetRole ?? null)
const skillProfile = computed(() => inputs.value?.skillProfile ?? null)
const skillEvidenceCount = computed(() => skillProfile.value?.skillEvidence?.length ?? 0)
const canGenerate = computed(
  () =>
    Boolean(targetRole.value?.targetRoleId) &&
    targetRole.value?.targetRoleVersion !== undefined &&
    skillProfile.value?.profileVersion !== undefined,
)

const statusCounts = computed(() => {
  const items = selectedSnapshot.value?.items ?? []
  return {
    ALL: items.length,
    MATCHED: countStatus(items, 'MATCHED'),
    PARTIAL: countStatus(items, 'PARTIAL'),
    MISSING: countStatus(items, 'MISSING'),
    UNVERIFIED: countStatus(items, 'UNVERIFIED'),
  }
})

const filteredItems = computed(() => {
  const items = selectedSnapshot.value?.items ?? []
  if (activeFilter.value === 'ALL') return items
  return items.filter((item) => item.status === activeFilter.value)
})

const selectedUsesCurrentInputs = computed(() => {
  const snapshot = selectedSnapshot.value
  if (!snapshot || !inputs.value?.targetRole) return true
  return (
    snapshot.targetRoleId === inputs.value.targetRole.targetRoleId &&
    snapshot.targetRoleVersion === inputs.value.targetRole.targetRoleVersion &&
    snapshot.profileVersion === inputs.value.skillProfile.profileVersion
  )
})

const filterOptions: Array<{ value: GapFilter; label: string }> = [
  { value: 'ALL', label: '全部要求' },
  { value: 'MATCHED', label: '已匹配' },
  { value: 'PARTIAL', label: '部分匹配' },
  { value: 'MISSING', label: '待补齐' },
  { value: 'UNVERIFIED', label: '待验证' },
]

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  pageError.value = ''
  historyError.value = ''
  operationError.value = ''
  try {
    const [inputResult, historyResult] = await Promise.allSettled([
      loadSkillGapInputs(),
      loadSkillGapHistory(),
    ])

    if (inputResult.status === 'fulfilled') {
      inputs.value = inputResult.value
    } else {
      pageError.value = toApiError(inputResult.reason).message
    }

    if (historyResult.status === 'fulfilled') {
      applyHistoryPage(historyResult.value, false)
    } else {
      historyError.value = toApiError(historyResult.reason).message
    }
  } finally {
    loading.value = false
  }
}

async function generateSnapshot() {
  if (!inputs.value || !canGenerate.value) return

  generating.value = true
  operationError.value = ''
  operationSuccess.value = ''
  try {
    const snapshot = await generateSkillGapFromReviewedInputs(inputs.value)
    snapshots.value = [
      snapshot,
      ...snapshots.value.filter((item) => item.snapshotId !== snapshot.snapshotId),
    ]
    selectedSnapshot.value = snapshot
    activeFilter.value = 'ALL'
    operationSuccess.value = '能力差距快照已生成。相同输入版本会安全复用已有快照。'
  } catch (error) {
    if (error instanceof SkillGapInputsChangedError) {
      inputs.value = error.latestInputs
      operationError.value = error.message
    } else {
      operationError.value = toApiError(error).message
      await refreshInputsAfterFailure()
    }
  } finally {
    generating.value = false
  }
}

async function refreshInputsAfterFailure() {
  try {
    inputs.value = await loadSkillGapInputs()
  } catch {
    // 生成错误优先展示；现有输入和历史快照继续保留。
  }
}

async function loadMoreHistory() {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  historyError.value = ''
  try {
    applyHistoryPage(await loadSkillGapHistory(nextCursor.value), true)
  } catch (error) {
    historyError.value = toApiError(error).message
  } finally {
    loadingMore.value = false
  }
}

function applyHistoryPage(page: SkillGapHistoryPage, append: boolean) {
  snapshots.value = append
    ? [
        ...snapshots.value,
        ...page.items.filter(
          (next) => !snapshots.value.some((item) => item.snapshotId === next.snapshotId),
        ),
      ]
    : page.items
  nextCursor.value = page.nextCursor
  hasMore.value = page.hasMore
  if (!append) {
    selectedSnapshot.value = page.items[0] ?? null
    activeFilter.value = 'ALL'
  }
}

function selectSnapshot(snapshot: SkillGapSnapshotResponse) {
  selectedSnapshot.value = snapshot
  activeFilter.value = 'ALL'
}

function countStatus(items: GapItem[], status: GapItemStatus) {
  return items.filter((item) => item.status === status).length
}

function statusMeta(status?: GapItemStatus) {
  const metadata: Record<GapItemStatus, { label: string; color: string; detail: string }> = {
    MATCHED: { label: '已匹配', color: 'green', detail: '存在已确认的项目证据' },
    PARTIAL: { label: '部分匹配', color: 'blue', detail: '存在相关证据但覆盖不足' },
    MISSING: { label: '待补齐', color: 'orange', detail: '当前技能画像没有同技能证据' },
    UNVERIFIED: { label: '待验证', color: 'purple', detail: '有自述证据但缺少项目证据' },
  }
  return status ? metadata[status] : { label: '未知状态', color: 'default', detail: '状态待确认' }
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
  <div class="skill-gap-page">
    <header class="skill-gap-hero">
      <div>
        <p>岗位能力诊断 · 第二步</p>
        <h2>用确认过的证据，看清真实能力差距</h2>
        <span>每份快照固定引用目标岗位、技能画像和算法版本，可以复现，也不会被前端改写。</span>
      </div>
      <Button @click="router.push(ROUTE_PATHS.targetRole)">
        <ArrowLeftOutlined />返回目标岗位
      </Button>
    </header>

    <Alert v-if="pageError" type="error" show-icon :message="pageError">
      <template #action><Button size="small" @click="loadPage">重试</Button></template>
    </Alert>

    <template v-if="loading">
      <section class="skill-gap-loading"><Skeleton active :paragraph="{ rows: 8 }" /></section>
    </template>

    <template v-else>
      <section class="input-snapshot" aria-labelledby="input-snapshot-title">
        <header>
          <div>
            <p>本次生成输入</p>
            <h3 id="input-snapshot-title">固定版本组合</h3>
          </div>
          <Button @click="loadPage"><ReloadOutlined />刷新输入与历史</Button>
        </header>

        <div class="input-snapshot__grid">
          <article :class="{ 'input-card--missing': !targetRole }">
            <div class="input-card__icon"><FileSearchOutlined /></div>
            <div>
              <span>目标岗位</span>
              <h4>{{ targetRole?.requirements?.jobTitle || '尚未确认目标岗位' }}</h4>
              <p>版本 {{ targetRole?.targetRoleVersion ?? '-' }}</p>
            </div>
            <Tag :color="targetRole ? 'green' : 'orange'">{{ targetRole ? '可用' : '缺失' }}</Tag>
          </article>

          <article>
            <div class="input-card__icon"><DatabaseOutlined /></div>
            <div>
              <span>已确认技能画像</span>
              <h4>{{ skillEvidenceCount }} 条技能证据</h4>
              <p>画像版本 {{ skillProfile?.profileVersion ?? '-' }}</p>
            </div>
            <Tag :color="skillEvidenceCount ? 'green' : 'gold'">
              {{ skillEvidenceCount ? '有证据' : '保守基线' }}
            </Tag>
          </article>
        </div>

        <Alert
          v-if="skillProfile && skillEvidenceCount === 0"
          type="warning"
          show-icon
          message="当前没有已确认技能证据"
          description="仍可生成保守基线，但岗位要求通常会被判定为待补齐。"
        />
        <Alert v-if="operationError" type="error" show-icon :message="operationError" />
        <Alert v-if="operationSuccess" type="success" show-icon :message="operationSuccess" />

        <div class="input-snapshot__action">
          <p>生成过程只执行确定性匹配，不调用模型评估熟练度。</p>
          <Button
            type="primary"
            size="large"
            :disabled="!canGenerate"
            :loading="generating"
            @click="generateSnapshot"
          >
            <BulbOutlined />{{ selectedSnapshot ? '按当前版本重新生成' : '生成能力差距快照' }}
          </Button>
        </div>
      </section>

      <section class="skill-gap-workspace">
        <aside class="snapshot-history" aria-labelledby="snapshot-history-title">
          <header>
            <div>
              <p>不可变历史</p>
              <h3 id="snapshot-history-title">快照记录</h3>
            </div>
            <span>{{ snapshots.length }} 份</span>
          </header>

          <Alert v-if="historyError" type="warning" show-icon :message="historyError" />
          <Empty v-if="!snapshots.length" description="尚无能力差距快照" />
          <div v-else class="snapshot-history__list">
            <button
              v-for="snapshot in snapshots"
              :key="snapshot.snapshotId"
              type="button"
              :class="{
                'snapshot-history__item--active':
                  selectedSnapshot?.snapshotId === snapshot.snapshotId,
              }"
              @click="selectSnapshot(snapshot)"
            >
              <span
                >岗位 v{{ snapshot.targetRoleVersion ?? '-' }} · 画像 v{{
                  snapshot.profileVersion ?? '-'
                }}</span
              >
              <strong>{{ snapshot.items?.length ?? 0 }} 项要求</strong>
              <small><ClockCircleOutlined />{{ formatTime(snapshot.createdAt) }}</small>
            </button>
          </div>
          <Button v-if="hasMore" block :loading="loadingMore" @click="loadMoreHistory">
            加载更早快照
          </Button>
        </aside>

        <article class="gap-detail" aria-labelledby="gap-detail-title">
          <template v-if="selectedSnapshot">
            <header class="gap-detail__header">
              <div>
                <p>当前查看</p>
                <h3 id="gap-detail-title">能力差距快照</h3>
                <span>
                  {{ selectedSnapshot.algorithmVersion || '算法版本待确认' }} ·
                  {{ formatTime(selectedSnapshot.createdAt) }}
                </span>
              </div>
              <Tag :color="selectedUsesCurrentInputs ? 'green' : 'orange'">
                {{ selectedUsesCurrentInputs ? '当前输入版本' : '历史输入版本' }}
              </Tag>
            </header>

            <Alert
              v-if="!selectedUsesCurrentInputs"
              type="warning"
              show-icon
              message="这是一份历史快照"
              description="目标岗位或技能画像版本已经变化，请按当前版本重新生成后再制定训练计划。"
            />

            <div class="gap-filters" aria-label="按差距状态筛选">
              <button
                v-for="option in filterOptions"
                :key="option.value"
                type="button"
                :class="{ 'gap-filter--active': activeFilter === option.value }"
                @click="activeFilter = option.value"
              >
                <span>{{ option.label }}</span>
                <strong>{{ statusCounts[option.value] }}</strong>
              </button>
            </div>

            <div v-if="filteredItems.length" class="gap-list">
              <article v-for="item in filteredItems" :key="item.gapItemId || item.requirementRef">
                <header>
                  <Tag :color="statusMeta(item.status).color">{{
                    statusMeta(item.status).label
                  }}</Tag>
                  <span>{{ item.requirementRef || '要求引用待确认' }}</span>
                </header>
                <h4>{{ item.requirementText || '岗位要求文本缺失' }}</h4>
                <p>{{ item.reason || statusMeta(item.status).detail }}</p>
                <footer>
                  <SafetyCertificateOutlined />
                  {{ item.evidenceMemoryIds?.length ?? 0 }} 条已确认证据引用
                </footer>
              </article>
            </div>
            <Empty v-else description="当前筛选条件下没有条目" />
          </template>

          <div v-else class="gap-detail__empty">
            <BulbOutlined />
            <h3 id="gap-detail-title">等待第一份能力差距快照</h3>
            <p>确认目标岗位后，按当前画像版本生成确定性差距结果。</p>
          </div>
        </article>
      </section>

      <aside class="skill-gap-principle">
        <WarningOutlined />
        <div>
          <h3>差距状态不是能力分数</h3>
          <p>它只描述“岗位要求是否有已确认且可追溯的同技能证据”，不能替代真实面试验证。</p>
        </div>
        <CheckCircleFilled />
      </aside>
    </template>
  </div>
</template>

<style scoped>
.skill-gap-page {
  display: grid;
  gap: 24px;
}

.skill-gap-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 28px;
  padding: 8px 0 4px;
}

.skill-gap-hero > div {
  max-width: 790px;
}

.skill-gap-hero p,
.input-snapshot header p,
.snapshot-history header p,
.gap-detail__header p {
  margin: 0 0 8px;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 750;
}

.skill-gap-hero h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(27px, 3vw, 38px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}

.skill-gap-hero > div > span {
  display: block;
  margin-top: 12px;
  color: var(--cf-color-text-muted);
}

.skill-gap-loading,
.input-snapshot,
.snapshot-history,
.gap-detail,
.skill-gap-principle {
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-panel);
  background: #fff;
}

.skill-gap-loading,
.input-snapshot {
  padding: 24px;
}

.input-snapshot {
  display: grid;
  gap: 18px;
}

.input-snapshot > header,
.snapshot-history > header,
.gap-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.input-snapshot h3,
.snapshot-history h3,
.gap-detail h3,
.skill-gap-principle h3 {
  margin: 0;
  color: #0f1d46;
  font-size: 19px;
}

.input-snapshot__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.input-snapshot__grid article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe2ff;
  border-radius: var(--cf-radius-card);
  background: #f7f8ff;
}

.input-snapshot__grid .input-card--missing {
  border-color: #fed7aa;
  background: #fffaf5;
}

.input-card__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  color: var(--cf-color-primary);
  font-size: 20px;
}

.input-snapshot__grid span,
.input-snapshot__grid p,
.input-snapshot__action p,
.gap-detail__header span,
.gap-list p,
.skill-gap-principle p {
  margin: 0;
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

.input-snapshot__grid h4 {
  margin: 3px 0;
  color: #172554;
  font-size: 17px;
}

.input-snapshot__action {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
}

.skill-gap-workspace {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  align-items: start;
  gap: 20px;
}

.snapshot-history,
.gap-detail {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.snapshot-history > header > span {
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

.snapshot-history__list {
  display: grid;
  gap: 8px;
}

.snapshot-history__item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 13px 14px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-control);
  background: #fff;
  color: var(--cf-color-text-secondary);
  text-align: left;
  cursor: pointer;
}

.snapshot-history__item:hover,
.snapshot-history__item--active {
  border-color: #a5b4fc;
  background: var(--cf-color-primary-soft);
}

.snapshot-history__item span,
.snapshot-history__item small {
  color: var(--cf-color-text-muted);
  font-size: 11px;
}

.snapshot-history__item strong {
  color: #1e2b52;
  font-size: 14px;
}

.snapshot-history__item small {
  display: flex;
  align-items: center;
  gap: 5px;
}

.gap-detail {
  min-height: 520px;
}

.gap-filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.gap-filters button {
  display: grid;
  gap: 4px;
  padding: 12px 8px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-control);
  background: #fff;
  color: var(--cf-color-text-secondary);
  cursor: pointer;
}

.gap-filters button:hover,
.gap-filters .gap-filter--active {
  border-color: #a5b4fc;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
}

.gap-filters span {
  font-size: 12px;
}

.gap-filters strong {
  font-size: 18px;
}

.gap-list {
  display: grid;
  gap: 12px;
}

.gap-list article {
  padding: 18px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-card);
  background: #fff;
}

.gap-list header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.gap-list header > span {
  overflow-wrap: anywhere;
  color: var(--cf-color-text-muted);
  font-size: 11px;
}

.gap-list h4 {
  margin: 12px 0 7px;
  color: #172554;
  font-size: 16px;
}

.gap-list footer {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 12px;
  color: var(--cf-color-success);
  font-size: 12px;
}

.gap-detail__empty {
  display: grid;
  min-height: 420px;
  place-content: center;
  justify-items: center;
  text-align: center;
}

.gap-detail__empty > :first-child {
  margin-bottom: 14px;
  color: #a5b4fc;
  font-size: 44px;
}

.gap-detail__empty p {
  margin: 8px 0 0;
  color: var(--cf-color-text-muted);
}

.skill-gap-principle {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 20px 22px;
  background: #f8fafc;
}

.skill-gap-principle > :first-child {
  color: var(--cf-color-warning);
  font-size: 22px;
}

.skill-gap-principle > :last-child {
  color: var(--cf-color-success);
  font-size: 20px;
}

@media (max-width: 1100px) {
  .skill-gap-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .snapshot-history__list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .skill-gap-hero,
  .input-snapshot__action {
    align-items: stretch;
    flex-direction: column;
  }

  .input-snapshot__grid,
  .snapshot-history__list {
    grid-template-columns: minmax(0, 1fr);
  }

  .gap-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .input-snapshot__grid article {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .input-snapshot__grid article > :last-child {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
