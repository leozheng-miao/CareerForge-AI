import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  api: {
    createQuickInterview: vi.fn(),
    getInterview: vi.fn(),
    getResumableInterview: vi.fn(),
    listInterviews: vi.fn(),
  },
}))

vi.mock('@/features/interview/api/interview-flow-api', async (original) => ({
  ...await original<typeof import('@/features/interview/api/interview-flow-api')>(),
  ...mocks.api,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}))

import InterviewsView from '@/views/app/InterviewsView.vue'

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(InterviewsView, { global: { plugins: [pinia] } })
}

describe('interview center', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.api.getResumableInterview.mockResolvedValue(null)
    mocks.api.listInterviews.mockResolvedValue({ items: [], nextCursor: null })
    mocks.api.createQuickInterview.mockResolvedValue({
      interviewId: 'quick-1',
      mode: 'QUICK',
      stage: 'PREPARING',
      version: 1,
    })
  })

  it('shows both interview modes and an honest recent-record empty state', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('.interview-mode-entry')).toHaveLength(2)
    expect(wrapper.text()).toContain('快速面试')
    expect(wrapper.text()).toContain('60 分钟 · 8 个考查目标')
    expect(wrapper.text()).toContain('综合面试')
    expect(wrapper.text()).toContain('结合岗位与真实经历')
    expect(wrapper.text()).toContain('还没有面试记录')
    expect(wrapper.text()).not.toContain('数据结构与算法')
  })

  it('puts an unfinished interview first and blocks both creation paths', async () => {
    mocks.api.getResumableInterview.mockResolvedValue({
      interviewId: 'active-1',
      mode: 'QUICK',
      currentQuestionNumber: 3,
      remainingSeconds: 2_400,
    })
    mocks.api.getInterview.mockResolvedValue({
      interviewId: 'active-1',
      mode: 'QUICK',
      stage: 'INTERVIEWING',
      version: 8,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('.active-interview').text()).toContain('继续第 3 个问题')
    expect(wrapper.get('.active-interview button').text()).toBe('继续面试')
    expect(
      wrapper
        .findAll('.interview-mode-entry button')
        .every((button) => button.attributes('disabled') !== undefined),
    ).toBe(true)

    await wrapper.get('.active-interview button').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith('/app/interviews/active-1/session')
  })

  it('creates a quick interview only after the resumable check succeeds', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="start-quick-interview"]').trigger('click')
    await flushPromises()

    expect(mocks.api.createQuickInterview).toHaveBeenCalledTimes(1)
    expect(mocks.routerPush).toHaveBeenCalledWith('/app/interviews/quick-1/ready')
  })

  it('keeps creation disabled when it cannot confirm whether an interview is active', async () => {
    mocks.api.getResumableInterview.mockRejectedValue(new Error('network unavailable'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂时无法确认是否有未完成面试')
    expect(
      wrapper
        .findAll('.interview-mode-entry button')
        .every((button) => button.attributes('disabled') !== undefined),
    ).toBe(true)
    expect(mocks.api.createQuickInterview).not.toHaveBeenCalled()
  })

  it('shows only three recent records from the existing list endpoint', async () => {
    mocks.api.listInterviews.mockResolvedValue({
      items: ['1', '2', '3', '4'].map((interviewId) => ({
        interviewId,
        mode: 'QUICK',
        stage: 'COMPLETED',
        submittedAnswerCount: 8,
        createdAt: '2026-10-12T08:00:00Z',
        version: 12,
      })),
      nextCursor: 'next-page',
    })

    const wrapper = mountView()
    await flushPromises()

    expect(mocks.api.listInterviews).toHaveBeenCalledWith(undefined, 4)
    expect(wrapper.findAll('.record-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('查看全部')
  })
})
