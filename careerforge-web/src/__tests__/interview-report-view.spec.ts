import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api/errors'
import InterviewReportSection from '@/features/interview/components/InterviewReportSection.vue'

const mocks = vi.hoisted(() => ({
  getInterview: vi.fn(),
  getInterviewHistory: vi.fn(),
  getInterviewReport: vi.fn(),
  retryInterviewReport: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('@/features/interview/api/interview-flow-api', async (original) => ({
  ...await original<typeof import('@/features/interview/api/interview-flow-api')>(),
  getInterview: mocks.getInterview,
  getInterviewHistory: mocks.getInterviewHistory,
  getInterviewReport: mocks.getInterviewReport,
  retryInterviewReport: mocks.retryInterviewReport,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { interviewId: 'interview-1' } }),
  useRouter: () => ({ push: mocks.routerPush }),
}))

import InterviewReportView from '@/views/app/InterviewReportView.vue'

const readyReport = {
  reportId: 'report-1',
  interviewId: 'interview-1',
  status: 'READY' as const,
  reportJson: JSON.stringify({
    summary: '回答能够围绕可靠性给出具体实现。',
    strengths: [
      {
        title: '可靠性意识清楚',
        description: '回答说明了幂等和有界重试的作用。',
        answerId: 'answer-1',
        quote: '我使用幂等键避免重复写入',
      },
    ],
    improvements: [
      {
        title: '补充失败边界',
        description: '可以进一步说明重试耗尽后的处理。',
        answerId: 'answer-1',
        quote: '失败后进入人工处理队列',
      },
    ],
    topicReviews: [
      { sequenceNo: 1, conclusion: '能够结合实现解释系统可靠性。', answerIds: ['answer-1'] },
    ],
  }),
  version: 2,
  updatedAt: '2026-10-13T08:00:00Z',
}

const history = [
  {
    question: { questionId: 'question-1', content: '如何避免任务重复执行？', displayOrder: 1 },
    answer: { submittedContent: '我使用幂等键避免重复写入，并限制重试次数。', status: 'SUBMITTED' },
  },
  {
    question: { questionId: 'question-2', content: '如何处理熔断？', displayOrder: 2 },
    answer: { status: 'SKIPPED' },
  },
]

async function mountReport() {
  const wrapper = mount(InterviewReportView)
  await flushPromises()
  return wrapper
}

describe('formal report section', () => {
  it('renders an evidence quote as a semantic quotation without invented scoring', () => {
    const wrapper = mount(InterviewReportSection, {
      props: {
        title: '做得好的地方',
        items: [{
          title: '可靠性意识',
          description: '说明了幂等与有界重试',
          quote: '我使用幂等键',
        }],
      },
    })

    expect(wrapper.get('blockquote').text()).toContain('我使用幂等键')
    expect(wrapper.text()).not.toMatch(/总分|排名|百分制|雷达|部分报告/)
  })

  it('keeps an empty report section readable', () => {
    const wrapper = mount(InterviewReportSection, {
      props: { title: '优先改进项', items: [] },
    })

    expect(wrapper.text()).toContain('暂无需要单独列出的内容')
    expect(wrapper.findAll('article')).toHaveLength(0)
  })
})

describe('formal report page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getInterview.mockResolvedValue({
      interviewId: 'interview-1',
      mode: 'QUICK',
      stage: 'GENERATING_REPORT',
      version: 4,
    })
    mocks.getInterviewHistory.mockResolvedValue(history)
    mocks.getInterviewReport.mockResolvedValue({
      ...readyReport,
      status: 'GENERATING',
      reportJson: undefined,
    })
    mocks.retryInterviewReport.mockResolvedValue({
      ...readyReport,
      status: 'PENDING',
      reportJson: undefined,
    })
  })

  it('shows only the generating state while the report task is running', async () => {
    const wrapper = await mountReport()

    expect(wrapper.text()).toContain('报告正在生成')
    expect(wrapper.text()).toContain('可以先离开')
    expect(wrapper.text()).not.toContain('做得好的地方')
    expect(wrapper.find('[data-testid="retry-report"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the failed state and retries only the report task', async () => {
    mocks.getInterviewReport.mockResolvedValue({
      ...readyReport,
      status: 'FAILED',
      reportJson: undefined,
      failureCode: 'MODEL_UNAVAILABLE',
    })
    const wrapper = await mountReport()

    expect(wrapper.text()).toContain('报告生成未完成')
    expect(wrapper.text()).toContain('题目和已提交回答不会改变')

    await wrapper.get('[data-testid="retry-report"]').trigger('click')
    await flushPromises()

    expect(mocks.retryInterviewReport).toHaveBeenCalledWith('interview-1')
    expect(wrapper.text()).toContain('报告正在生成')
    wrapper.unmount()
  })

  it('shows the no-report state when a completed interview has no valid submitted answer', async () => {
    mocks.getInterview.mockResolvedValue({
      interviewId: 'interview-1',
      mode: 'QUICK',
      stage: 'COMPLETED',
      version: 5,
    })
    mocks.getInterviewReport.mockRejectedValue(new ApiError({
      kind: 'not-found',
      message: '报告不存在',
    }))
    const wrapper = await mountReport()

    expect(wrapper.text()).toContain('没有生成正式报告')
    expect(wrapper.text()).toContain('没有可用于评价的已提交回答')
    expect(wrapper.text()).not.toContain('报告正在生成')
    expect(wrapper.find('[data-testid="retry-report"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders the ready report in reading order with semantic answer quotations', async () => {
    mocks.getInterviewReport.mockResolvedValue(readyReport)
    const wrapper = await mountReport()

    const text = wrapper.text()
    expect(text.indexOf('总结')).toBeLessThan(text.indexOf('做得好的地方'))
    expect(text.indexOf('做得好的地方')).toBeLessThan(text.indexOf('优先改进项'))
    expect(text.indexOf('优先改进项')).toBeLessThan(text.indexOf('考查回顾'))
    expect(text.indexOf('考查回顾')).toBeLessThan(text.indexOf('完整问答记录'))
    expect(wrapper.findAll('blockquote')).toHaveLength(3)
    expect(text).toContain('我使用幂等键避免重复写入，并限制重试次数。')
    expect(text).toContain('已跳过')
    expect(text).not.toMatch(/总分|排名|百分制|雷达|部分报告/)
    wrapper.unmount()
  })

  it('shows explicit empty copy for report arrays instead of leaving blank regions', async () => {
    mocks.getInterviewReport.mockResolvedValue({
      ...readyReport,
      reportJson: JSON.stringify({
        summary: '本场回答较少，仅整理已确认内容。',
        strengths: [],
        improvements: [],
        topicReviews: [],
      }),
    })
    mocks.getInterviewHistory.mockResolvedValue([])
    const wrapper = await mountReport()

    expect(wrapper.text()).toContain('暂无需要单独列出的内容')
    expect(wrapper.text()).toContain('暂无可回顾的已考查主题')
    expect(wrapper.text()).toContain('暂无可展示的问答记录')
    wrapper.unmount()
  })

  it('recovers from malformed published report JSON without regenerating the report', async () => {
    mocks.getInterviewReport
      .mockResolvedValueOnce({ ...readyReport, reportJson: '{not-json' })
      .mockResolvedValueOnce(readyReport)
    const wrapper = await mountReport()

    expect(wrapper.text()).toContain('报告内容暂时无法读取')
    expect(wrapper.text()).toContain('不会重新生成或修改已发布报告')
    expect(wrapper.find('[data-testid="reload-report"]').exists()).toBe(true)

    await wrapper.get('[data-testid="reload-report"]').trigger('click')
    await flushPromises()

    expect(mocks.retryInterviewReport).not.toHaveBeenCalled()
    expect(mocks.getInterviewReport).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('回答能够围绕可靠性给出具体实现')
    wrapper.unmount()
  })

  it('keeps the records exit available when the report cannot be loaded', async () => {
    mocks.getInterview.mockRejectedValue(new ApiError({
      kind: 'unavailable',
      message: '服务暂时不可用，请稍后重试',
    }))
    const wrapper = await mountReport()

    expect(wrapper.text()).toContain('暂时无法读取报告')
    expect(wrapper.get('[data-testid="back-to-records"]').exists()).toBe(true)

    await wrapper.get('[data-testid="back-to-records"]').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith('/app/interview-records')
    wrapper.unmount()
  })
})
