/* oxlint-disable vitest/require-mock-type-parameters */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const fetchEventSourceMock = vi.hoisted(() => vi.fn())

vi.mock('@microsoft/fetch-event-source', () => ({
  EventStreamContentType: 'text/event-stream',
  fetchEventSource: fetchEventSourceMock,
}))

import { connectAuthenticatedSse } from '@/shared/sse/sse-client'

describe('authenticated SSE protocol', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('sends the saved Last-Event-ID, deduplicates it and converges on terminal event', async () => {
    sessionStorage.setItem('careerforge:sse:coach:u1:r1', '100-0')
    const delivered: string[] = []
    const terminal = vi.fn<() => void>()
    fetchEventSourceMock.mockImplementation(async (_url, options) => {
      expect(options.headers['Last-Event-ID']).toBe('100-0')
      await options.onopen(
        new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
      )
      options.onmessage({ id: '100-0', event: 'RUN_STARTED', data: '{}' })
      options.onmessage({ id: '101-0', event: 'RUN_COMPLETED', data: '{"status":"SUCCEEDED"}' })
      options.onclose()
    })

    await connectAuthenticatedSse({
      path: '/api/coaching-runs/r1/events',
      resumeKey: 'coach:u1:r1',
      terminalEvents: new Set(['RUN_COMPLETED']),
      onEvent: (event) => {
        delivered.push(event.id ?? '')
      },
      onTerminal: terminal,
    })

    expect(delivered).toEqual(['101-0'])
    expect(terminal).toHaveBeenCalledOnce()
    expect(sessionStorage.getItem('careerforge:sse:coach:u1:r1')).toBe('101-0')
  })

  it('stops after the finite reconnect budget is exhausted', async () => {
    fetchEventSourceMock.mockImplementation(async (_url, options) => {
      for (let index = 0; index < 5; index += 1) options.onerror(new Error('network'))
    })
    await expect(
      connectAuthenticatedSse({
        path: '/events',
        resumeKey: 'finite',
        terminalEvents: new Set(),
        onEvent: vi.fn<() => void>(),
        onTerminal: vi.fn<() => void>(),
      }),
    ).rejects.toThrow('重连次数已达上限')
  })
})
