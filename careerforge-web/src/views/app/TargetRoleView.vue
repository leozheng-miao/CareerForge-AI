<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  CheckCircleFilled,
  FileSearchOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Checkbox, Input, Skeleton, Tag } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import {
  confirmReviewedTargetRoleDraft,
  loadLatestTargetRole,
  reloadTargetRoleDraft,
  submitTargetRoleDraft,
  TargetRoleDraftChangedError,
} from '@/features/target-role/api/target-role-api'
import { ROUTE_PATHS } from '@/router/paths'
import type {
  JobRequirements,
  TargetRoleDraftResponse,
  TargetRoleResponse,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

const SOURCE_REF_MAX_LENGTH = 128
const JD_MAX_LENGTH = 12_000

const router = useRouter()
const currentTarget = ref<TargetRoleResponse | null>(null)
const draft = ref<TargetRoleDraftResponse | null>(null)
const sourceRef = ref('手动粘贴 JD')
const jdText = ref('')
const reviewed = ref(false)
const loadingCurrent = ref(true)
const creatingDraft = ref(false)
const confirmingDraft = ref(false)
const pageError = ref('')
const operationError = ref('')
const operationSuccess = ref('')
const reviewPanel = ref<HTMLElement | null>(null)

const draftGroups = computed(() => buildRequirementGroups(draft.value?.requirements))
const currentRequirementCount = computed(() => countRequirements(currentTarget.value?.requirements))
const canConfirm = computed(
  () =>
    reviewed.value &&
    draft.value?.status === 'PENDING' &&
    !creatingDraft.value &&
    !confirmingDraft.value,
)

onMounted(refreshCurrentTarget)

async function refreshCurrentTarget() {
  loadingCurrent.value = true
  pageError.value = ''
  try {
    currentTarget.value = await loadLatestTargetRole()
  } catch (error) {
    pageError.value = toApiError(error).message
  } finally {
    loadingCurrent.value = false
  }
}

async function createDraft() {
  operationError.value = ''
  operationSuccess.value = ''

  const normalizedSourceRef = sourceRef.value.trim()
  const normalizedJdText = jdText.value.trim()
  if (!normalizedSourceRef) {
    operationError.value = '请填写 JD 来源，例如公司和岗位名称'
    return
  }
  if (!normalizedJdText) {
    operationError.value = '请粘贴需要解析的完整 JD 文本'
    return
  }

  creatingDraft.value = true
  reviewed.value = false
  try {
    draft.value = await submitTargetRoleDraft({
      sourceRef: normalizedSourceRef,
      jdText: normalizedJdText,
    })
    operationSuccess.value = '草案已生成。请核对结构化要求，确认后才会创建正式目标岗位。'
    await nextTick()
    reviewPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    operationError.value = toApiError(error).message
  } finally {
    creatingDraft.value = false
  }
}

async function confirmDraft() {
  const reviewedDraft = draft.value
  if (!reviewedDraft || !canConfirm.value) return

  confirmingDraft.value = true
  operationError.value = ''
  operationSuccess.value = ''
  try {
    const confirmedTarget = await confirmReviewedTargetRoleDraft(reviewedDraft)
    currentTarget.value = confirmedTarget
    draft.value = {
      ...reviewedDraft,
      status: 'CONFIRMED',
      confirmedTargetRoleId: confirmedTarget.targetRoleId,
      confirmedTargetRoleVersion: confirmedTarget.targetRoleVersion,
      confirmedAt: confirmedTarget.confirmedAt,
    }
    reviewed.value = false
    operationSuccess.value = '目标岗位已确认并冻结为新版本，可以进入能力差距诊断。'
  } catch (error) {
    reviewed.value = false
    if (error instanceof TargetRoleDraftChangedError) {
      draft.value = error.latestDraft
      operationError.value = error.message
    } else {
      operationError.value = toApiError(error).message
      await refreshDraftAfterFailure(reviewedDraft.draftId)
    }
  } finally {
    confirmingDraft.value = false
  }
}

async function refreshDraftAfterFailure(draftId?: string) {
  if (!draftId) return
  try {
    draft.value = await reloadTargetRoleDraft(draftId)
  } catch {
    // 保留用户刚刚审阅的草案；原始确认错误仍是页面主错误。
  }
}

function buildRequirementGroups(requirements?: JobRequirements) {
  if (!requirements) return []
  return [
    { key: 'languages', title: '编程语言', items: requirements.programmingLanguages ?? [] },
    {
      key: 'backend',
      title: '后端与基础设施',
      items: requirements.backendAndInfrastructureRequirements ?? [],
    },
    { key: 'agent', title: 'Agent 能力', items: requirements.agentRequirements ?? [] },
    { key: 'rag', title: 'RAG 能力', items: requirements.ragRequirements ?? [] },
    { key: 'engineering', title: '工程能力', items: requirements.engineeringRequirements ?? [] },
    { key: 'responsibilities', title: '岗位职责', items: requirements.responsibilities ?? [] },
    { key: 'interview', title: '面试重点', items: requirements.interviewTopics ?? [] },
    { key: 'bonus', title: '加分项', items: requirements.bonusQualifications ?? [] },
  ].filter((group) => group.items.length > 0)
}

function countRequirements(requirements?: JobRequirements) {
  return buildRequirementGroups(requirements).reduce(
    (total, group) => total + group.items.length,
    0,
  )
}

function formatTime(value?: string) {
  if (!value) return '暂无时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间待确认'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <div class="target-role-page">
    <header class="target-role-hero">
      <div>
        <p>岗位能力诊断 · 第一步</p>
        <h2>把一份真实 JD，冻结为可追溯的目标岗位</h2>
        <span>模型只负责生成结构化草案；你确认后，服务端才创建不可变的目标岗位版本。</span>
      </div>
      <div class="target-role-steps" aria-label="目标岗位建立流程">
        <span class="target-role-steps__active">1 输入 JD</span>
        <span>2 审阅草案</span>
        <span>3 人工确认</span>
      </div>
    </header>

    <Alert v-if="pageError" type="error" show-icon :message="pageError">
      <template #action>
        <Button size="small" @click="refreshCurrentTarget"><ReloadOutlined />重试</Button>
      </template>
    </Alert>

    <section class="current-target" aria-labelledby="current-target-title">
      <header>
        <div>
          <p>当前事实快照</p>
          <h3 id="current-target-title">最新确认目标</h3>
        </div>
        <div class="current-target__actions">
          <Button
            v-if="currentTarget"
            type="primary"
            ghost
            @click="router.push(ROUTE_PATHS.skillGaps)"
          >
            进入能力差距诊断
          </Button>
          <Button :loading="loadingCurrent" @click="refreshCurrentTarget">
            <ReloadOutlined />刷新
          </Button>
        </div>
      </header>
      <Skeleton v-if="loadingCurrent" active :paragraph="{ rows: 2 }" />
      <template v-else-if="currentTarget">
        <div class="current-target__content">
          <div class="current-target__icon"><SafetyCertificateOutlined /></div>
          <div>
            <h4>{{ currentTarget.requirements?.jobTitle || '岗位名称待确认' }}</h4>
            <p>
              版本 {{ currentTarget.targetRoleVersion ?? '-' }} ·
              {{ currentRequirementCount }} 条结构化要求 ·
              {{ formatTime(currentTarget.confirmedAt) }}确认
            </p>
          </div>
          <Tag color="green"><CheckCircleFilled />服务端已冻结</Tag>
        </div>
      </template>
      <div v-else class="current-target__empty">
        <InfoCircleOutlined />
        <span>尚未确认目标岗位。请从下方真实 JD 开始。</span>
      </div>
    </section>

    <section class="target-role-workspace" aria-label="创建并确认目标岗位">
      <form class="jd-form" @submit.prevent="createDraft">
        <div class="section-heading">
          <span>01</span>
          <div>
            <h3>输入岗位信息</h3>
            <p>JD 属于不可信外部输入，后端负责校验、解析和保存来源摘要。</p>
          </div>
        </div>

        <label for="source-ref">JD 来源</label>
        <Input
          id="source-ref"
          v-model:value="sourceRef"
          :maxlength="SOURCE_REF_MAX_LENGTH"
          placeholder="例如：某公司｜AI Agent 应用开发工程师"
          :disabled="creatingDraft || confirmingDraft"
        />

        <label for="jd-text">完整 JD 文本</label>
        <Input.TextArea
          id="jd-text"
          v-model:value="jdText"
          :maxlength="JD_MAX_LENGTH"
          show-count
          :auto-size="{ minRows: 14, maxRows: 22 }"
          placeholder="粘贴岗位职责、任职要求、加分项等完整文本……"
          :disabled="creatingDraft || confirmingDraft"
        />

        <p class="jd-form__privacy">
          <SafetyCertificateOutlined />不要粘贴姓名、电话、邮箱、Token 或公司内部机密。
        </p>
        <Button type="primary" size="large" html-type="submit" block :loading="creatingDraft">
          <FileSearchOutlined />{{ creatingDraft ? '正在解析并保存草案' : '生成待确认草案' }}
        </Button>
      </form>

      <article ref="reviewPanel" class="draft-review" aria-labelledby="draft-review-title">
        <div class="section-heading">
          <span>02</span>
          <div>
            <h3 id="draft-review-title">审阅结构化草案</h3>
            <p>确认前请检查岗位名称、职责和能力要求是否符合原始 JD。</p>
          </div>
        </div>

        <Alert v-if="operationError" type="error" show-icon :message="operationError" />
        <Alert v-if="operationSuccess" type="success" show-icon :message="operationSuccess" />

        <div v-if="!draft" class="draft-review__empty">
          <FileSearchOutlined />
          <h4>等待生成草案</h4>
          <p>提交左侧 JD 后，这里会展示服务端返回的结构化结果。</p>
        </div>

        <template v-else>
          <header class="draft-review__summary">
            <div>
              <Tag :color="draft.status === 'PENDING' ? 'gold' : 'green'">
                {{ draft.status === 'PENDING' ? '待人工确认' : '已确认' }}
              </Tag>
              <h4>{{ draft.requirements?.jobTitle || '岗位名称未识别' }}</h4>
              <p>
                草案版本 {{ draft.version ?? '-' }} · {{ draftGroups.length }} 类要求 ·
                {{ formatTime(draft.createdAt) }}生成
              </p>
            </div>
          </header>

          <div v-if="draftGroups.length" class="requirement-groups">
            <section v-for="group in draftGroups" :key="group.key">
              <h5>
                {{ group.title }} <span>{{ group.items.length }}</span>
              </h5>
              <ul>
                <li v-for="item in group.items" :key="item">{{ item }}</li>
              </ul>
            </section>
          </div>
          <Alert
            v-else
            type="warning"
            show-icon
            message="草案没有返回可展示的结构化要求，请不要确认并重新生成"
          />

          <div class="draft-confirmation">
            <Checkbox
              v-model:checked="reviewed"
              :disabled="draft.status !== 'PENDING' || confirmingDraft"
            >
              我已对照原始 JD 审阅以上内容，并确认将其冻结为新的目标岗位版本
            </Checkbox>
            <Button
              type="primary"
              size="large"
              :disabled="!canConfirm || draftGroups.length === 0"
              :loading="confirmingDraft"
              @click="confirmDraft"
            >
              <CheckCircleFilled />确认目标岗位
            </Button>
          </div>
        </template>
      </article>
    </section>

    <aside class="target-role-principle">
      <InfoCircleOutlined />
      <div>
        <h3>确认会创建新版本，不会原地覆盖旧目标</h3>
        <p>后续能力差距、训练计划和模拟面试都引用明确的目标岗位 ID 与版本。</p>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.target-role-page {
  display: grid;
  gap: 24px;
}

.target-role-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding: 8px 0 4px;
}

.target-role-hero > div:first-child {
  max-width: 760px;
}

.target-role-hero p,
.current-target header p {
  margin: 0 0 8px;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 750;
}

.target-role-hero h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(27px, 3vw, 38px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}

.target-role-hero > div > span {
  display: block;
  margin-top: 12px;
  color: var(--cf-color-text-muted);
}

.target-role-steps {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.target-role-steps span {
  padding: 7px 12px;
  border: 1px solid var(--cf-color-border);
  border-radius: 999px;
  background: #fff;
  color: var(--cf-color-text-muted);
  font-size: 12px;
  font-weight: 650;
}

.target-role-steps .target-role-steps__active {
  border-color: #c7d2fe;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
}

.current-target,
.jd-form,
.draft-review,
.target-role-principle {
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-panel);
  background: #fff;
}

.current-target {
  padding: 20px 22px;
}

.current-target > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.current-target__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.current-target h3,
.section-heading h3,
.target-role-principle h3 {
  margin: 0;
  color: #0f1d46;
  font-size: 18px;
}

.current-target__content {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border-radius: var(--cf-radius-card);
  background: #f7f8ff;
}

.current-target__icon {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  color: var(--cf-color-success);
  font-size: 22px;
}

.current-target__content h4,
.draft-review__summary h4 {
  margin: 0;
  color: #0b1740;
  font-size: 20px;
}

.current-target__content p,
.draft-review__summary p,
.current-target__empty,
.section-heading p,
.draft-review__empty p,
.target-role-principle p {
  margin: 5px 0 0;
  color: var(--cf-color-text-muted);
}

.current-target__empty {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px;
  border-radius: var(--cf-radius-card);
  background: #f8fafc;
}

.target-role-workspace {
  display: grid;
  grid-template-columns: minmax(320px, 0.85fr) minmax(420px, 1.15fr);
  align-items: start;
  gap: 20px;
}

.jd-form,
.draft-review {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--cf-color-border);
}

.section-heading > span {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 50%;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-size: 12px;
  font-weight: 800;
}

.section-heading p {
  font-size: 13px;
}

.jd-form label {
  color: var(--cf-color-text-secondary);
  font-size: 14px;
  font-weight: 650;
}

.jd-form__privacy {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}

.draft-review {
  min-height: 590px;
}

.draft-review__empty {
  display: grid;
  min-height: 390px;
  place-content: center;
  justify-items: center;
  padding: 32px;
  text-align: center;
}

.draft-review__empty > :first-child {
  margin-bottom: 12px;
  color: #a5b4fc;
  font-size: 40px;
}

.draft-review__empty h4 {
  margin: 0;
  color: #27345b;
  font-size: 18px;
}

.draft-review__summary {
  padding: 18px;
  border: 1px solid #dbe2ff;
  border-radius: var(--cf-radius-card);
  background: #f7f8ff;
}

.draft-review__summary h4 {
  margin-top: 10px;
}

.requirement-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.requirement-groups section {
  padding: 16px;
  border: 1px solid var(--cf-color-border);
  border-radius: var(--cf-radius-card);
  background: #fff;
}

.requirement-groups h5 {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 10px;
  color: #27345b;
  font-size: 14px;
}

.requirement-groups h5 span {
  color: var(--cf-color-primary);
}

.requirement-groups ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: var(--cf-color-text-secondary);
  font-size: 13px;
}

.draft-confirmation {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: var(--cf-radius-card);
  background: var(--cf-color-background-warm);
}

.draft-confirmation button {
  justify-self: end;
}

.target-role-principle {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 22px;
  background: #f8fafc;
}

.target-role-principle > :first-child {
  margin-top: 3px;
  color: var(--cf-color-primary);
  font-size: 20px;
}

@media (max-width: 1100px) {
  .target-role-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .target-role-steps {
    justify-content: flex-start;
  }

  .target-role-workspace {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .current-target__content {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .current-target__content > :last-child {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .requirement-groups {
    grid-template-columns: minmax(0, 1fr);
  }

  .jd-form,
  .draft-review {
    padding: 18px;
  }

  .draft-confirmation button {
    width: 100%;
  }
}
</style>
