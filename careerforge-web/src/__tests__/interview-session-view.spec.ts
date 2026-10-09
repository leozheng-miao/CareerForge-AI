import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import InterviewAnswerEditor from '@/features/interview/components/InterviewAnswerEditor.vue'
import InterviewQuestionPanel from '@/features/interview/components/InterviewQuestionPanel.vue'
import InterviewTopBar from '@/features/interview/components/InterviewTopBar.vue'
import InterviewDetailView from '@/views/app/InterviewDetailView.vue'

const session = {
  interviewId: 'interview-1',
  mode: 'QUICK' as const,
  stage: 'INTERVIEWING' as const,
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
    store.restore.mockClear()
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
})
