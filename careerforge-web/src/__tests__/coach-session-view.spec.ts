/* oxlint-disable vitest/require-mock-type-parameters */
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const coachApi = vi.hoisted(() => ({
  loadWorkspace: vi.fn(),
  submitRun: vi.fn(),
}))

const coachSse = vi.hoisted(() => ({
  observe: vi.fn(),
}))

vi.mock('@/features/coach/api/coach-api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/features/coach/api/coach-api')>()
  return {
    ...original,
    loadCoachWorkspace: coachApi.loadWorkspace,
    submitCoachRun: coachApi.submitRun,
  }
})

vi.mock('@/features/coach/api/coach-sse', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/features/coach/api/coach-sse')>()
  return {
    ...original,
    observeCoachRun: coachSse.observe,
  }
})

import { useAuthStore } from '@/features/auth/store/auth-store'
import CoachSessionDetailView from '@/views/app/CoachSessionDetailView.vue'

describe('Career Coach session chat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      value: vi.fn(),
    })
    coachApi.loadWorkspace.mockResolvedValue({
      session: { sessionId: 'session-1', title: '每周复盘', status: 'ACTIVE', version: 1 },
      turns: [],
      runs: [],
      turnsCursor: null,
      hasOlderTurns: false,
    })
    coachSse.observe.mockReturnValue(new Promise(() => undefined))
  })

  it('puts the user message into the chat immediately and keeps progress inside the conversation', async () => {
    let resolveSubmission: ((value: { runId: string; status: string }) => void) | undefined
    coachApi.submitRun.mockReturnValue(
      new Promise((resolve) => {
        resolveSubmission = resolve
      }),
    )

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/app/coach', component: { template: '<div />' } },
        { path: '/app/coach/:sessionId', component: CoachSessionDetailView },
      ],
    })
    await router.push('/app/coach/session-1')
    await router.isReady()
    const pinia = createPinia()
    const authStore = useAuthStore(pinia)
    authStore.acceptSession({
      userId: 'user-1',
      email: 'user@example.com',
      displayName: 'User',
      tokenType: 'Bearer',
      accessToken: 'test-token',
      accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    })
    const wrapper = mount(CoachSessionDetailView, {
      global: { plugins: [pinia, router] },
    })
    await flushPromises()

    await wrapper.get('textarea').setValue('请帮我复盘本周训练')
    const sendButton = wrapper.findAll('button').find((button) => button.text().includes('发送'))
    await sendButton?.trigger('click')

    expect(wrapper.find('.turn--optimistic').text()).toContain('请帮我复盘本周训练')
    expect(wrapper.find('.turn--typing').exists()).toBe(true)
    expect(wrapper.find('.progress-strip').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('正在分析')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')

    resolveSubmission?.({ runId: 'run-1', status: 'RECEIVED' })
    await flushPromises()

    expect(coachSse.observe).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
