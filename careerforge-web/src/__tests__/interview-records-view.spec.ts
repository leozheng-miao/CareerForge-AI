import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  InterviewRecord,
  InterviewStage,
} from '@/features/interview/api/interview-flow-api'
import InterviewRecordCard from '@/features/interview/components/InterviewRecordCard.vue'

const mocks = vi.hoisted(() => ({
  listInterviews: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('@/features/interview/api/interview-flow-api', async (original) => ({
  ...await original<typeof import('@/features/interview/api/interview-flow-api')>(),
  listInterviews: mocks.listInterviews,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}))

import InterviewRecordsView from '@/views/app/InterviewRecordsView.vue'

function record(stage: InterviewStage, interviewId: string = stage): InterviewRecord {
  return {
    interviewId,
    mode: 'QUICK',
    stage,
    submittedAnswerCount: 2,
    createdAt: '2026-10-12T08:00:00Z',
    version: 3,
  }
}

describe('interview record row', () => {
  it.each([
    ['PREPARING', '查看准备进度'],
    ['READY', '开始面试'],
    ['INTERVIEWING', '继续面试'],
    ['GENERATING_REPORT', '查看报告进度'],
    ['COMPLETED', '查看报告'],
    ['FAILED', '查看详情'],
  ] satisfies Array<[InterviewStage, string]>)('shows one primary action for %s', async (stage, action) => {
    const value = record(stage)
    const wrapper = mount(InterviewRecordCard, { props: { record: value } })

    expect(wrapper.findAll('button')).toHaveLength(1)
    expect(wrapper.get('button').text()).toBe(action)
    expect(wrapper.text()).toContain('已提交 2 个回答')

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('open')).toEqual([[value]])
  })

  it('labels comprehensive records without showing the quick label', () => {
    const wrapper = mount(InterviewRecordCard, {
      props: { record: { ...record('COMPLETED'), mode: 'COMPREHENSIVE' } },
    })

    expect(wrapper.text()).toContain('综合面试')
    expect(wrapper.text()).not.toContain('快速面试')
  })
})

describe('interview records page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listInterviews.mockResolvedValue({ items: [], nextCursor: null })
  })

  it('shows an empty state when there are no records', async () => {
    const wrapper = mount(InterviewRecordsView)
    await flushPromises()

    expect(wrapper.text()).toContain('还没有面试记录')
  })

  it('appends the next cursor page without replacing existing records', async () => {
    mocks.listInterviews
      .mockResolvedValueOnce({ items: [record('COMPLETED', 'first')], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [record('COMPLETED', 'second')], nextCursor: null })

    const wrapper = mount(InterviewRecordsView)
    await flushPromises()
    await wrapper.get('[data-testid="load-more-records"]').trigger('click')
    await flushPromises()

    expect(mocks.listInterviews).toHaveBeenNthCalledWith(1, undefined)
    expect(mocks.listInterviews).toHaveBeenNthCalledWith(2, 'cursor-1')
    expect(wrapper.findAll('.record-row')).toHaveLength(2)
    expect(wrapper.find('[data-testid="load-more-records"]').exists()).toBe(false)
  })

  it.each([
    ['PREPARING', '/app/interviews/PREPARING/ready'],
    ['INTERVIEWING', '/app/interviews/INTERVIEWING/session'],
    ['COMPLETED', '/app/interviews/COMPLETED/report'],
  ] satisfies Array<[InterviewStage, string]>)('routes %s records to their single destination', async (stage, destination) => {
    mocks.listInterviews.mockResolvedValue({ items: [record(stage)], nextCursor: null })
    const wrapper = mount(InterviewRecordsView)
    await flushPromises()

    await wrapper.get('.record-row button').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith(destination)
  })
})
