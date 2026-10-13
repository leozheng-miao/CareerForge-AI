import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { Select } from 'ant-design-vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api/errors'
import type { ComprehensiveSetup } from '@/features/interview/api/interview-flow-api'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import InterviewSettingsPanel from '@/features/interview/components/InterviewSettingsPanel.vue'
import ProfileDraftEditor from '@/features/interview/components/ProfileDraftEditor.vue'
import ResumeUploadField from '@/features/interview/components/ResumeUploadField.vue'

const mocks = vi.hoisted(() => ({
  api: {
    createComprehensiveInterview: vi.fn(),
    getInterview: vi.fn(),
    parseResume: vi.fn(),
    updateComprehensiveSetup: vi.fn(),
  },
  leaveGuard: null as null | (() => boolean),
  routeQuery: {} as Record<string, string>,
  routerPush: vi.fn(),
}))

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: () => boolean) => { mocks.leaveGuard = guard },
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({ push: mocks.routerPush }),
}))

vi.mock('@/features/interview/api/interview-flow-api', async (original) => ({
  ...await original<typeof import('@/features/interview/api/interview-flow-api')>(),
  ...mocks.api,
}))

import ComprehensiveInterviewSetupView from '@/views/app/ComprehensiveInterviewSetupView.vue'

const baseSetup: ComprehensiveSetup = {
  jobDescription: '负责 Java Agent 应用的后端开发',
  salaryRange: '30K-40K/月',
  personalDescription: '希望重点练习状态机和 RAG',
  durationSeconds: 3600,
  difficulty: 'STANDARD',
}

function mountView(initialSetup?: ComprehensiveSetup) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (initialSetup) useInterviewFlowStore(pinia).comprehensiveSetup = initialSetup
  return mount(ComprehensiveInterviewSetupView, { global: { plugins: [pinia] } })
}

async function fillJobStep(wrapper: VueWrapper) {
  await wrapper.get('textarea[name="jobDescription"]').setValue(baseSetup.jobDescription)
  wrapper.findComponent(Select).vm.$emit('update:value', baseSetup.salaryRange)
  await nextTick()
}

async function goToSettings(wrapper: VueWrapper) {
  await fillJobStep(wrapper)
  await wrapper.get('[data-testid="next-step"]').trigger('click')
  await wrapper.get('[data-testid="next-profile-step"]').trigger('click')
}

describe('comprehensive interview setup', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.leaveGuard = null
    delete mocks.routeQuery.interviewId
    window.confirm = vi.fn(() => true)
    mocks.api.createComprehensiveInterview.mockResolvedValue({
      interviewId: 'c-1', mode: 'COMPREHENSIVE', stage: 'PREPARING', version: 0,
    })
    mocks.api.getInterview.mockResolvedValue({
      interviewId: 'existing-1', mode: 'COMPREHENSIVE', stage: 'PREPARING', version: 3,
    })
    mocks.api.updateComprehensiveSetup.mockResolvedValue({
      interviewId: 'existing-1', mode: 'COMPREHENSIVE', stage: 'PREPARING', version: 4,
    })
  })

  it('keeps the user on job information until JD and salary are complete', async () => {
    const wrapper = mountView()

    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.get('.setup-step-error').text()).toContain('请填写岗位 JD')
    expect(wrapper.get('[data-current-step]').attributes('data-current-step')).toBe('1')

    await wrapper.get('textarea[name="jobDescription"]').setValue(baseSetup.jobDescription)
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    expect(wrapper.get('.setup-step-error').text()).toContain('请选择薪资范围')
    expect(wrapper.get('[data-current-step]').attributes('data-current-step')).toBe('1')
  })

  it('uses the backend-supported monthly salary bands', () => {
    const wrapper = mountView()
    const salary = wrapper.findComponent(Select)

    expect(salary.props('options')).toEqual(expect.arrayContaining([
      { label: '20K-30K/月', value: '20K-30K/月' },
      { label: '30K-40K/月', value: '30K-40K/月' },
      { label: '80K/月以上', value: '80K/月以上' },
    ]))
  })

  it('keeps job fields and confirmed profile while moving backward and forward', async () => {
    const wrapper = mountView()
    await fillJobStep(wrapper)
    await wrapper.get('textarea[name="personalDescription"]').setValue(baseSetup.personalDescription)
    await wrapper.get('[data-testid="next-step"]').trigger('click')

    const parsed = {
      personalSummary: 'Java Agent 后端开发者',
      coreSkills: ['Java', 'Redis'],
      projectExperiences: [{
        name: '水水面', role: '后端开发', period: '2026',
        description: '面试工作流', technologies: ['Spring Boot'],
      }],
      workExperiences: [],
      internshipExperiences: [],
    }
    wrapper.findComponent(ResumeUploadField).vm.$emit('parsed', parsed)
    await nextTick()
    await wrapper.get('[data-testid="next-profile-step"]').trigger('click')
    await wrapper.get('[data-testid="previous-settings-step"]').trigger('click')
    await wrapper.get('[data-testid="previous-step"]').trigger('click')

    expect((wrapper.get('textarea[name="jobDescription"]').element as HTMLTextAreaElement).value)
      .toBe(baseSetup.jobDescription)
    expect((wrapper.get('textarea[name="personalDescription"]').element as HTMLTextAreaElement).value)
      .toBe(baseSetup.personalDescription)
    expect(wrapper.findComponent(ProfileDraftEditor).props('modelValue')).toEqual(parsed)
  })

  it('shows resume parsing errors locally and keeps the no-resume path available', async () => {
    mocks.api.parseResume.mockRejectedValue(new ApiError({
      kind: 'validation', message: '简历内容无法解析',
    }))
    const wrapper = mount(ResumeUploadField)
    const fileInput = wrapper.get('input[type="file"]')
    const file = new File(['not-a-real-pdf'], 'resume.pdf', { type: 'application/pdf' })
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [file] })

    await fileInput.trigger('change')
    await flushPromises()

    expect(wrapper.text()).toContain('简历内容无法解析')
    expect(wrapper.text()).toContain('不上传简历也可以继续')
    expect(wrapper.emitted('failed')).toHaveLength(1)
  })

  it('asks before selecting a resume that would replace an edited profile', async () => {
    vi.mocked(window.confirm).mockReturnValue(false)
    const wrapper = mount(ResumeUploadField, { props: { confirmReplace: true } })
    const fileInput = wrapper.get('input[type="file"]')
    const click = vi.spyOn(fileInput.element as HTMLInputElement, 'click')

    await wrapper.get('button').trigger('click')

    expect(window.confirm).toHaveBeenCalledWith('新简历的解析结果会替换当前画像，是否继续选择文件？')
    expect(click).not.toHaveBeenCalled()
  })

  it('submits the exact three-step summary without requiring a resume', async () => {
    const wrapper = mountView()
    await goToSettings(wrapper)
    wrapper.findComponent(InterviewSettingsPanel).vm.$emit('update:durationSeconds', 5400)
    wrapper.findComponent(InterviewSettingsPanel).vm.$emit('update:difficulty', 'CHALLENGE')
    await nextTick()

    expect(wrapper.get('.setup-summary').text()).toContain('90 分钟')
    expect(wrapper.get('.setup-summary').text()).toContain('挑战')
    expect(wrapper.get('.setup-summary').text()).toContain('未提供候选人画像')

    await wrapper.get('[data-testid="submit-setup"]').trigger('click')
    await flushPromises()

    expect(mocks.api.createComprehensiveInterview).toHaveBeenCalledWith({
      jobDescription: baseSetup.jobDescription,
      salaryRange: baseSetup.salaryRange,
      personalDescription: undefined,
      confirmedProfile: undefined,
      durationSeconds: 5400,
      difficulty: 'CHALLENGE',
    })
    expect(mocks.routerPush).toHaveBeenCalledWith('/app/interviews/c-1/ready')
  })

  it('updates the existing interview from the interviewId query', async () => {
    mocks.routeQuery.interviewId = 'existing-1'
    const wrapper = mountView(baseSetup)
    await wrapper.get('[data-testid="next-step"]').trigger('click')
    await wrapper.get('[data-testid="next-profile-step"]').trigger('click')
    await wrapper.get('[data-testid="submit-setup"]').trigger('click')
    await flushPromises()

    expect(mocks.api.getInterview).toHaveBeenCalledWith('existing-1')
    expect(mocks.api.updateComprehensiveSetup).toHaveBeenCalledTimes(1)
    expect(mocks.routerPush).toHaveBeenCalledWith('/app/interviews/existing-1/ready')
  })

  it('asks before leaving after the user changes unsubmitted content', async () => {
    const wrapper = mountView()
    await wrapper.get('textarea[name="jobDescription"]').setValue('尚未提交的 JD')
    vi.mocked(window.confirm).mockReturnValue(false)

    expect(mocks.leaveGuard?.()).toBe(false)
    expect(window.confirm).toHaveBeenCalledWith('当前填写的内容尚未提交，确定要离开吗？')
  })
})
