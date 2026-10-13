import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import InterviewAnswerEditor from '@/features/interview/components/InterviewAnswerEditor.vue'
import InterviewQuestionPanel from '@/features/interview/components/InterviewQuestionPanel.vue'
import InterviewTopBar from '@/features/interview/components/InterviewTopBar.vue'
import type { InterviewSession } from '@/features/interview/api/interview-flow-api'
import InterviewDetailView from '@/views/app/InterviewDetailView.vue'

const session: InterviewSession = {
  interviewId: 'interview-1',
  mode: 'QUICK',
  stage: 'INTERVIEWING',
  version: 8,
}

const store = reactive({
  session,
  question: {
    questionId: 'question-4-follow-up',
    type: 'FOLLOW_UP' as const,
    displayOrder: 42,
    content: '请继续说明你如何处理失败恢复。',
    draft: '',
    answerVersion: 0,
    sessionVersion: 8,
  } as {
    questionId: string
    type: 'MAIN' | 'FOLLOW_UP'
    displayOrder: number
    content: string
    draft: string
    answerVersion: number
    sessionVersion: number
  } | null,
  timer: {
    interviewId: 'interview-1',
    timerState: 'RUNNING',
    remainingSeconds: 2_400,
    version: 8,
    leaseActive: true,
  },
  waiting: false,
  restore: vi.fn().mockResolvedValue(session),
  saveDraft: vi.fn(),
  submit: vi.fn(),
  skip: vi.fn(),
  pause: vi.fn(),
  resume: vi.fn(),
  finish: vi.fn(),
  loadQuestionThenResume: vi.fn(),
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { interviewId: 'interview-1' } }),
  useRouter: () => ({ replace: vi.fn() }),
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

describe('active interview', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    store.session = session
    store.question = {
      questionId: 'question-4-follow-up',
      type: 'FOLLOW_UP',
      displayOrder: 42,
      content: '请继续说明你如何处理失败恢复。',
      draft: '',
      answerVersion: 0,
      sessionVersion: 8,
    }
    store.waiting = false
    store.timer.timerState = 'RUNNING'
    store.timer.remainingSeconds = 2_400
    store.restore.mockClear()
    store.loadQuestionThenResume.mockClear()
    store.pause.mockClear()
    store.resume.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the full long question immediately', () => {
    const content = '请结合一个真实项目，完整说明你如何设计状态机、幂等、租约恢复和模型降级。'.repeat(4)
    const wrapper = mount(InterviewQuestionPanel, {
      props: {
        question: {
          questionId: 'q',
          type: 'MAIN',
          displayOrder: 10,
          content,
          draft: '',
          answerVersion: 0,
          sessionVersion: 1,
        },
      },
    })

    expect(wrapper.text()).toContain(content)
    expect(wrapper.text()).not.toContain('查看完整题目')
  })

  it('freezes editor after submit acceptance', () => {
    const wrapper = mount(InterviewAnswerEditor, {
      props: { modelValue: 'answer', frozen: true, saving: false },
    })

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(
      wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined),
    ).toBe(true)
    expect(wrapper.text()).toContain('回答已提交并冻结')
  })

  it('keeps the current objective progress while the next question is loading', async () => {
    const wrapper = mount(InterviewDetailView)
    await flushPromises()

    expect(wrapper.findComponent(InterviewTopBar).props('progress')).toBe(4)

    store.question = null
    store.waiting = true
    await nextTick()

    expect(wrapper.findComponent(InterviewTopBar).props('progress')).toBe(4)
    wrapper.unmount()
  })

  it('renders comprehensive mode with six-theme progress', async () => {
    store.session = { ...session, mode: 'COMPREHENSIVE' }
    store.question = {
      questionId: 'comprehensive-topic-6',
      type: 'MAIN',
      displayOrder: 60,
      content: '请说明你的架构权衡。',
      draft: '',
      answerVersion: 0,
      sessionVersion: 8,
    }
    store.timer.remainingSeconds = 5_400

    const wrapper = mount(InterviewDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('综合面试')
    expect(wrapper.text()).toContain('6 / 6 个主题')
    expect(wrapper.text()).not.toContain('8 个考查目标')
    wrapper.unmount()
  })

  it('keeps the interview running when the browser tab becomes hidden', async () => {
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    const wrapper = mount(InterviewDetailView)
    await flushPromises()

    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()

    expect(store.pause).not.toHaveBeenCalled()
    expect(store.resume).not.toHaveBeenCalled()
    hidden.mockRestore()
    wrapper.unmount()
  })

  it('keeps the answer read-only until a paused interview is resumed', async () => {
    store.timer.timerState = 'PAUSED'
    const wrapper = mount(InterviewDetailView)
    await flushPromises()

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('面试已暂停，继续后可以作答')
    const resumeButton = wrapper.findAll('button')
      .find((button) => button.text().includes('继续面试'))
    await resumeButton?.trigger('click')

    expect(store.resume).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('keeps the timer stopped while the next question is being prepared', async () => {
    store.question = null
    store.waiting = true
    const wrapper = mount(InterviewDetailView)
    await flushPromises()

    const initialRemaining = wrapper.findComponent(InterviewTopBar).props('remainingSeconds')
    await vi.advanceTimersByTimeAsync(5_000)

    expect(wrapper.findComponent(InterviewTopBar).props('remainingSeconds')).toBe(initialRemaining)
    expect(wrapper.text()).toContain('等待期间不计入 60 分钟有效作答时间')
    expect(wrapper.text()).toContain('已提交内容保持冻结')
    const waitingButton = wrapper.findAll('button')
      .find((button) => button.text().includes('等待下一题'))
    expect(waitingButton?.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('allows the current answer to be completed after effective time reaches zero', async () => {
    store.timer.timerState = 'EXHAUSTED'
    store.timer.remainingSeconds = 0
    const wrapper = mount(InterviewDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('处理本题后面试结束')
    expect(wrapper.get('textarea').attributes('disabled')).toBeUndefined()
    await wrapper.get('textarea').setValue('这是当前题的最后回答')
    const submit = wrapper.findAll('button').find((button) => button.text().includes('提交回答'))

    expect(submit?.attributes('disabled')).toBeUndefined()
    expect(wrapper.findComponent(InterviewTopBar).props('timeExhausted')).toBe(true)
    wrapper.unmount()
  })
})
