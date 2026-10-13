<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CheckOutlined,
} from '@ant-design/icons-vue'
import { Alert, Button, Select, message } from 'ant-design-vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import type {
  ComprehensiveSetup,
  ConfirmedProfile,
  InterviewDifficulty,
} from '@/features/interview/api/interview-flow-api'
import InterviewSettingsPanel from '@/features/interview/components/InterviewSettingsPanel.vue'
import ProfileDraftEditor from '@/features/interview/components/ProfileDraftEditor.vue'
import ResumeUploadField from '@/features/interview/components/ResumeUploadField.vue'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { ROUTE_PATHS } from '@/router/paths'
import { toApiError } from '@/shared/api/errors'

type SetupStep = 1 | 2 | 3

const route = useRoute()
const router = useRouter()
const store = useInterviewFlowStore()
const existingId = computed(() => (
  typeof route.query.interviewId === 'string' ? route.query.interviewId : null
))
const previous = existingId.value ? store.comprehensiveSetup : null
const currentStep = ref<SetupStep>(1)
const jobDescription = ref(previous?.jobDescription ?? '')
const salaryRange = ref<string | undefined>(previous?.salaryRange || undefined)
const personalDescription = ref(previous?.personalDescription ?? '')
const profile = ref<ConfirmedProfile>(
  previous?.confirmedProfile ? cloneProfile(previous.confirmedProfile) : emptyProfile(),
)
const durationSeconds = ref<3600 | 5400>(previous?.durationSeconds ?? 3600)
const difficulty = ref<InterviewDifficulty>(previous?.difficulty ?? 'STANDARD')
const submitting = ref(false)
const dirty = ref(false)
const allowNavigation = ref(false)
const stepError = ref('')

const salaryOptions = [
  '10K/月以下', '10K-15K/月', '15K-20K/月', '20K-30K/月',
  '30K-40K/月', '40K-60K/月', '60K-80K/月', '80K/月以上',
].map((value) => ({ label: value, value }))
const steps: Array<{ number: SetupStep; title: string; description: string }> = [
  { number: 1, title: '岗位信息', description: '确认这次面试针对的岗位' },
  { number: 2, title: '个人材料', description: '可选简历和本次候选人画像' },
  { number: 3, title: '面试设置', description: '选择时长和难度后确认' },
]
const difficultyLabel = computed(() => ({
  BASIC: '基础', STANDARD: '标准', CHALLENGE: '挑战',
})[difficulty.value])
const profileSummary = computed(() => {
  if (!hasProfile(profile.value)) return '未提供候选人画像'
  const experienceCount = profile.value.projectExperiences.length
    + profile.value.workExperiences.length
    + profile.value.internshipExperiences.length
  return `${profile.value.coreSkills.length} 项核心技能 · ${experienceCount} 段经历`
})

onBeforeRouteLeave(() => {
  if (allowNavigation.value || !dirty.value) return true
  return window.confirm('当前填写的内容尚未提交，确定要离开吗？')
})

function markDirty() {
  dirty.value = true
}

function validateJobStep() {
  if (!jobDescription.value.trim()) return '请填写岗位 JD'
  if (!salaryRange.value) return '请选择薪资范围'
  return ''
}

function nextStep() {
  if (currentStep.value === 1) {
    stepError.value = validateJobStep()
    if (stepError.value) return
  }
  stepError.value = ''
  if (currentStep.value < 3) currentStep.value = (currentStep.value + 1) as SetupStep
}

function previousStep() {
  stepError.value = ''
  if (currentStep.value > 1) currentStep.value = (currentStep.value - 1) as SetupStep
}

function applyParsedProfile(value: ConfirmedProfile) {
  profile.value = cloneProfile(value)
  markDirty()
}

function updateProfile(value: ConfirmedProfile) {
  profile.value = value
  markDirty()
}

function updateDuration(value: 3600 | 5400) {
  durationSeconds.value = value
  markDirty()
}

function updateDifficulty(value: InterviewDifficulty) {
  difficulty.value = value
  markDirty()
}

function buildSetup(): ComprehensiveSetup {
  return {
    jobDescription: jobDescription.value.trim(),
    salaryRange: salaryRange.value!,
    personalDescription: personalDescription.value.trim() || undefined,
    confirmedProfile: hasProfile(profile.value) ? cloneProfile(profile.value) : undefined,
    durationSeconds: durationSeconds.value,
    difficulty: difficulty.value,
  }
}

async function submit() {
  stepError.value = validateJobStep()
  if (stepError.value) {
    currentStep.value = 1
    return
  }
  submitting.value = true
  try {
    const setup = buildSetup()
    if (existingId.value) await store.restore(existingId.value)
    const session = existingId.value
      ? await store.updateComprehensive(setup)
      : await store.createComprehensive(setup)
    allowNavigation.value = true
    await router.push(`/app/interviews/${session.interviewId}/ready`)
  } catch (error) {
    message.error(toApiError(error).message)
  } finally {
    submitting.value = false
  }
}

function emptyProfile(): ConfirmedProfile {
  return {
    personalSummary: '',
    coreSkills: [],
    projectExperiences: [],
    workExperiences: [],
    internshipExperiences: [],
  }
}

function cloneProfile(value: ConfirmedProfile): ConfirmedProfile {
  const cloneExperiences = (items: ConfirmedProfile['projectExperiences']) => items.map((item) => ({
    ...item,
    technologies: [...item.technologies],
  }))
  return {
    personalSummary: value.personalSummary,
    coreSkills: [...value.coreSkills],
    projectExperiences: cloneExperiences(value.projectExperiences),
    workExperiences: cloneExperiences(value.workExperiences),
    internshipExperiences: cloneExperiences(value.internshipExperiences),
  }
}

function hasProfile(value: ConfirmedProfile) {
  return Boolean(
    value.personalSummary.trim()
    || value.coreSkills.length
    || value.projectExperiences.length
    || value.workExperiences.length
    || value.internshipExperiences.length,
  )
}
</script>

<template>
  <section class="setup-page">
    <header class="setup-page__header">
      <Button type="text" class="setup-page__back" @click="router.push(ROUTE_PATHS.interviews)">
        <ArrowLeftOutlined aria-hidden="true" />
        返回面试中心
      </Button>
      <p>综合面试</p>
      <h2>{{ existingId ? '修改面试设置' : '准备一场更贴近目标岗位的面试。' }}</h2>
      <span>只有你确认的岗位和个人材料会用于本场面试。</span>
    </header>

    <nav class="setup-progress" :data-current-step="currentStep" aria-label="综合面试配置进度">
      <div
        v-for="step in steps"
        :key="step.number"
        class="setup-progress__item"
        :class="{
          'setup-progress__item--active': currentStep === step.number,
          'setup-progress__item--complete': currentStep > step.number,
        }"
      >
        <span class="setup-progress__number">
          <CheckOutlined v-if="currentStep > step.number" aria-hidden="true" />
          <template v-else>{{ step.number }}</template>
        </span>
        <div>
          <strong>{{ step.title }}</strong>
          <small>{{ step.description }}</small>
        </div>
      </div>
    </nav>

    <main class="setup-panel">
      <section v-show="currentStep === 1" class="setup-step" data-step="1" aria-labelledby="job-step-title">
        <header class="setup-step__heading">
          <p>第 1 步</p>
          <h3 id="job-step-title">岗位信息</h3>
          <span>岗位 JD 和薪资范围用于确定考查深度，不会被当作你的真实能力。</span>
        </header>

        <Alert v-if="stepError" class="setup-step-error" type="warning" show-icon :message="stepError" />

        <label class="setup-field">
          <span>岗位 JD <em>必填</em></span>
          <textarea
            name="jobDescription"
            :value="jobDescription"
            rows="10"
            maxlength="30000"
            placeholder="粘贴完整的岗位描述"
            @input="jobDescription = ($event.target as HTMLTextAreaElement).value; markDirty()"
          />
          <small>{{ jobDescription.length }} / 30000</small>
        </label>

        <label class="setup-field">
          <span>薪资范围 <em>必填，税前月薪</em></span>
          <Select
            v-model:value="salaryRange"
            aria-label="薪资范围（必填）"
            size="large"
            placeholder="请选择薪资范围"
            :options="salaryOptions"
            @change="markDirty"
          />
        </label>

        <label class="setup-field">
          <span>补充个人描述 <em>可选</em></span>
          <textarea
            name="personalDescription"
            :value="personalDescription"
            rows="4"
            maxlength="5000"
            placeholder="例如：想重点练习状态恢复、RAG 和 Agent 可靠性"
            @input="personalDescription = ($event.target as HTMLTextAreaElement).value; markDirty()"
          />
          <small>{{ personalDescription.length }} / 5000</small>
        </label>

        <footer class="setup-step__actions setup-step__actions--end">
          <Button data-testid="next-step" type="primary" size="large" @click="nextStep">
            下一步：个人材料
            <ArrowRightOutlined aria-hidden="true" />
          </Button>
        </footer>
      </section>

      <section v-show="currentStep === 2" class="setup-step" data-step="2" aria-labelledby="profile-step-title">
        <header class="setup-step__heading">
          <p>第 2 步</p>
          <h3 id="profile-step-title">个人材料与候选人画像</h3>
          <span>上传简历是可选的。解析结果必须由你检查和修改后，才会进入面试上下文。</span>
        </header>

        <div class="setup-material-block">
          <div>
            <h4>从简历提取</h4>
            <p>只支持 PDF 和 DOCX。原始文件仅用于当次解析。</p>
          </div>
          <ResumeUploadField
            :confirm-replace="hasProfile(profile)"
            @parsed="applyParsedProfile"
          />
        </div>

        <div class="setup-profile-block">
          <div>
            <h4>确认本场面试使用的画像</h4>
            <p>未识别的字段保持为空，不会自动补写经历。</p>
          </div>
          <ProfileDraftEditor :model-value="profile" @update:model-value="updateProfile" />
        </div>

        <p class="setup-skip-note">不上传简历也可以继续，届时将主要根据岗位 JD 准备面试。</p>

        <footer class="setup-step__actions">
          <Button data-testid="previous-step" size="large" @click="previousStep">
            <ArrowLeftOutlined aria-hidden="true" />
            上一步
          </Button>
          <Button data-testid="next-profile-step" type="primary" size="large" @click="nextStep">
            下一步：面试设置
            <ArrowRightOutlined aria-hidden="true" />
          </Button>
        </footer>
      </section>

      <section v-show="currentStep === 3" class="setup-step" data-step="3" aria-labelledby="settings-step-title">
        <header class="setup-step__heading">
          <p>第 3 步</p>
          <h3 id="settings-step-title">面试设置与最后确认</h3>
          <span>设置会影响面试时长和问题深度，不会提前展示具体题目。</span>
        </header>

        <InterviewSettingsPanel
          :duration-seconds="durationSeconds"
          :difficulty="difficulty"
          @update:duration-seconds="updateDuration"
          @update:difficulty="updateDifficulty"
        />

        <article class="setup-summary">
          <header>
            <p>最后确认</p>
            <h4>准备前再检查一次。</h4>
          </header>
          <dl>
            <div><dt>岗位方向</dt><dd>AI Agent 开发</dd></div>
            <div><dt>薪资范围</dt><dd>{{ salaryRange }}</dd></div>
            <div><dt>有效作答时长</dt><dd>{{ durationSeconds === 5400 ? '90 分钟' : '60 分钟' }}</dd></div>
            <div><dt>难度</dt><dd>{{ difficultyLabel }}</dd></div>
            <div><dt>个人材料</dt><dd>{{ profileSummary }}</dd></div>
          </dl>
          <div class="setup-summary__jd">
            <strong>岗位 JD</strong>
            <p>{{ jobDescription }}</p>
          </div>
        </article>

        <footer class="setup-step__actions">
          <Button data-testid="previous-settings-step" size="large" @click="previousStep">
            <ArrowLeftOutlined aria-hidden="true" />
            上一步
          </Button>
          <Button
            data-testid="submit-setup"
            type="primary"
            size="large"
            :loading="submitting"
            @click="submit"
          >
            {{ existingId ? '重新准备面试' : '确认并准备面试' }}
          </Button>
        </footer>
      </section>
    </main>
  </section>
</template>

<style scoped>
.setup-page {
  width: min(100%, 960px);
  margin: 0 auto;
  padding: 10px 0 72px;
}

.setup-page__header {
  padding: 12px 0 34px;
}

.setup-page__back {
  margin: 0 0 34px -12px;
  color: var(--ssm-color-text-secondary);
}

.setup-page__header > p,
.setup-step__heading > p,
.setup-summary header > p {
  margin: 0 0 10px;
  color: var(--ssm-color-brand);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.06em;
}

.setup-page__header h2 {
  max-width: 760px;
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: clamp(34px, 5vw, 54px);
  letter-spacing: -0.045em;
  line-height: 1.16;
}

.setup-page__header > span {
  display: block;
  margin-top: 16px;
  color: var(--ssm-color-text-secondary);
  font-size: 16px;
}

.setup-progress {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--ssm-color-line);
  border-bottom: 1px solid var(--ssm-color-line);
}

.setup-progress__item {
  display: flex;
  min-height: 96px;
  align-items: center;
  gap: 14px;
  padding: 18px 22px;
  color: var(--ssm-color-text-secondary);
}

.setup-progress__item + .setup-progress__item {
  border-left: 1px solid var(--ssm-color-line);
}

.setup-progress__number {
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ssm-color-line);
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
}

.setup-progress__item div {
  display: grid;
  gap: 4px;
}

.setup-progress__item strong {
  color: var(--ssm-color-ink);
}

.setup-progress__item small {
  line-height: 1.45;
}

.setup-progress__item--active {
  background: var(--ssm-color-brand-soft);
}

.setup-progress__item--active .setup-progress__number,
.setup-progress__item--complete .setup-progress__number {
  border-color: var(--ssm-color-brand);
  background: var(--ssm-color-brand);
  color: #fff;
}

.setup-panel {
  min-height: 620px;
  padding: 42px 0 0;
}

.setup-step {
  display: grid;
  gap: 28px;
}

.setup-step__heading {
  max-width: 720px;
}

.setup-step__heading h3 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: clamp(28px, 4vw, 38px);
  letter-spacing: -0.035em;
}

.setup-step__heading > span {
  display: block;
  margin-top: 12px;
  color: var(--ssm-color-text-secondary);
  line-height: 1.7;
}

.setup-field {
  display: grid;
  gap: 9px;
}

.setup-field > span {
  color: var(--ssm-color-ink);
  font-weight: 650;
}

.setup-field em {
  margin-left: 5px;
  color: var(--ssm-color-text-secondary);
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
}

.setup-field textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-control);
  background: var(--ssm-color-surface);
  color: var(--ssm-color-ink);
  font: inherit;
  line-height: 1.7;
  resize: vertical;
}

.setup-field textarea:focus {
  border-color: var(--ssm-color-brand);
  outline: 3px solid color-mix(in srgb, var(--ssm-color-brand) 18%, transparent);
}

.setup-field > small {
  justify-self: end;
  color: var(--ssm-color-text-secondary);
}

.setup-field :deep(.ant-select) {
  width: 100%;
}

.setup-material-block,
.setup-profile-block {
  display: grid;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-card);
  background: var(--ssm-color-surface);
}

.setup-material-block h4,
.setup-profile-block h4,
.setup-summary h4 {
  margin: 0;
  color: var(--ssm-color-ink);
  font-size: 20px;
}

.setup-material-block p,
.setup-profile-block p {
  margin: 8px 0 0;
  color: var(--ssm-color-text-secondary);
}

.setup-skip-note {
  margin: 0;
  padding-left: 14px;
  border-left: 3px solid var(--ssm-color-brand);
  color: var(--ssm-color-text-secondary);
  line-height: 1.7;
}

.setup-summary {
  overflow: hidden;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-card);
  background: var(--ssm-color-surface);
}

.setup-summary header {
  padding: 24px;
  border-bottom: 1px solid var(--ssm-color-line);
}

.setup-summary dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
}

.setup-summary dl > div {
  display: grid;
  gap: 6px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--ssm-color-line);
}

.setup-summary dl > div:nth-child(odd) {
  border-right: 1px solid var(--ssm-color-line);
}

.setup-summary dt {
  color: var(--ssm-color-text-secondary);
  font-size: 12px;
}

.setup-summary dd {
  margin: 0;
  color: var(--ssm-color-ink);
  font-weight: 650;
}

.setup-summary__jd {
  padding: 22px 24px;
}

.setup-summary__jd p {
  max-height: 150px;
  overflow: auto;
  margin: 10px 0 0;
  color: var(--ssm-color-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}

.setup-step__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-top: 8px;
}

.setup-step__actions--end {
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .setup-progress {
    grid-template-columns: 1fr;
  }

  .setup-progress__item {
    min-height: 72px;
  }

  .setup-progress__item + .setup-progress__item {
    border-top: 1px solid var(--ssm-color-line);
    border-left: 0;
  }

  .setup-progress__item small {
    display: none;
  }

  .setup-panel {
    padding-top: 32px;
  }

  .setup-summary dl {
    grid-template-columns: 1fr;
  }

  .setup-summary dl > div:nth-child(odd) {
    border-right: 0;
  }

  .setup-step__actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  .setup-step__actions .ant-btn {
    width: 100%;
  }
}
</style>
