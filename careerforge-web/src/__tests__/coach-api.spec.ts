/* oxlint-disable vitest/require-mock-type-parameters */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const generated = vi.hoisted(() => ({
  createRun: vi.fn(),
  extract: vi.fn(),
}))

vi.mock('@/shared/api/generated/careerforge', () => ({
  createCoachingRun: generated.createRun,
  extractMemoryCandidates: generated.extract,
  createCoachingSession: vi.fn(),
  getCoachingRun: vi.fn(),
  getCoachingSession: vi.fn(),
  listCoachingRuns: vi.fn(),
  listCoachingSessions: vi.fn(),
  listCoachingTurns: vi.fn(),
}))

import {
  extractMemoriesFromTurns,
  mergeTurns,
  submitCoachRun,
} from '@/features/coach/api/coach-api'

describe('asynchronous Career Coach workflow', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submits a new idempotent Run against the reviewed session version', async () => {
    generated.createRun.mockResolvedValue({ code: 0, data: { runId: 'r1', status: 'RECEIVED' } })
    await submitCoachRun({ sessionId: 's1', version: 4, status: 'ACTIVE' }, '  help me  ')
    expect(generated.createRun).toHaveBeenCalledWith({
      sessionId: 's1',
      requestId: expect.any(String),
      expectedSessionVersion: 4,
      message: 'help me',
    })
  })

  it('only sends eligible completed turn identities to memory extraction', async () => {
    generated.extract.mockResolvedValue({ code: 0, data: [] })
    await extractMemoriesFromTurns('s1', [
      { turnId: 'u1', memoryExtractionEligible: false },
      { turnId: 'a1', memoryExtractionEligible: true },
    ])
    expect(generated.extract).toHaveBeenCalledWith('s1', { turnIds: ['a1'] })
  })

  it('deduplicates recovered turns and preserves server sequence', () => {
    expect(
      mergeTurns(
        [{ turnId: 't2', turnSequence: 2, content: 'old' }],
        [
          { turnId: 't1', turnSequence: 1 },
          { turnId: 't2', turnSequence: 2, content: 'new' },
        ],
      ).map((turn) => turn.content),
    ).toEqual([undefined, 'new'])
  })
})
