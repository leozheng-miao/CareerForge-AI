import { reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import InterviewWaitingState from '@/features/interview/components/InterviewWaitingState.vue'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
}))

const store = reactive({
  session: {
    interviewId: 'interview-1',
    mode: 'QUICK' as 'QUICK' | 'COMPREHENSIVE',
    stage: 'PREPARING' as 'PREPARING' | 'READY' | 'INTERVIEWING' | 'GENERATING_REPORT' | 'COMPLETED' | 'FAILED',
    version: 1,
  },
  comprehensiveSetup: null as null | {
    jobDescription: string
    salaryRange: string
    durationSeconds: 3600 | 5400
    difficulty: 'BASIC' | 'STANDARD' | 'CHALLENGE'
  },
  enter: vi.fn(),
  restore: vi.fn(),
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { interviewId: 'interview-1' } }),
  useRouter: () => ({ push: mocks.routerPush, replace: mocks.routerReplace }),
}))

vi.mock('@/features/interview/store/interview-flow-store', () => ({
  useInterviewFlowStore: () => store,
}))

vi.mock('@/features/auth/store/auth-store', () => ({
  useAuthStore: () => ({ currentUser: { userId: 'owner-1' } }),
}))

vi.mock('@/features/interview/api/interview-flow-sse', () => ({
  observeInterviewFlow: () => new Promise(() => undefined),
}))

import InterviewReadyView from '@/views/app/InterviewReadyView.vue'

describe('interview ready and waiting states', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    store.session = {
      interviewId: 'interview-1',
      mode: 'QUICK',
      stage: 'PREPARING',
      version: 1,
    }
    store.comprehensiveSetup = null
    store.restore.mockImplementation(async () => store.session)
    store.enter.mockImplementation(async () => {
      store.session = { ...store.session, stage: 'INTERVIEWING', version: 2 }
      return store.session
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows a safe preparation state that can be left without exposing questions', async () => {
    const wrapper = mount(InterviewReadyView)
    await flushPromises()

    expect(wrapper.text()).toContain('正在准备')
    expect(wrapper.text()).toContain('可以先离开')
    expect(wrapper.text()).not.toContain('题目 1')
    expect(wrapper.get('[data-testid="start-interview"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('shows the confirmed comprehensive settings before the user starts', async () => {
    store.session = { ...store.session, mode: 'COMPREHENSIVE', stage: 'READY' }
    store.comprehensiveSetup = {
      jobDescription: '负责 Java Agent 平台开发',
      salaryRange: '30K-40K/月',
      durationSeconds: 5400,
      difficulty: 'CHALLENGE',
    }

    const wrapper = mount(InterviewReadyView)
    await flushPromises()

    expect(wrapper.text()).toContain('综合面试')
    expect(wrapper.text()).toContain('90 分钟')
    expect(wrapper.text()).toContain('挑战')
    expect(wrapper.text()).toContain('返回修改设置')
    expect(wrapper.text()).not.toContain('负责 Java Agent 平台开发')
    wrapper.unmount()
  })

  it('does not invent comprehensive settings after a direct page restore', async () => {
    store.session = { ...store.session, mode: 'COMPREHENSIVE', stage: 'READY' }
    store.comprehensiveSetup = null

    const wrapper = mount(InterviewReadyView)
    await flushPromises()

    expect(wrapper.text()).toContain('已按本场设置保存')
    expect(wrapper.text()).not.toContain('60 分钟')
    expect(wrapper.text()).not.toContain('标准')
    wrapper.unmount()
  })

  it('starts only after explicit confirmation and enters the immersive session', async () => {
    store.session = { ...store.session, stage: 'READY' }
    const wrapper = mount(InterviewReadyView)
    await flushPromises()

    await wrapper.get('[data-testid="start-interview"]').trigger('click')
    await flushPromises()

    expect(store.enter).toHaveBeenCalledTimes(1)
    expect(mocks.routerReplace).toHaveBeenCalledWith('/app/interviews/interview-1/session')
    wrapper.unmount()
  })

  it('explains that quick analysis time is paused without disclosing topics', () => {
    const wrapper = mount(InterviewWaitingState, { props: { mode: 'QUICK' } })

    expect(wrapper.text()).toContain('不计入 60 分钟')
    expect(wrapper.text()).not.toContain('内部考查目标')
  })

  it('does not claim comprehensive interviews are fixed at 60 minutes', () => {
    const wrapper = mount(InterviewWaitingState, { props: { mode: 'COMPREHENSIVE' } })

    expect(wrapper.text()).toContain('不计入本场有效作答时间')
    expect(wrapper.text()).not.toContain('不计入 60 分钟')
  })
})
