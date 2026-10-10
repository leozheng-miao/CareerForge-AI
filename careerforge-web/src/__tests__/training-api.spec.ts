import { describe, expect, it, vi } from 'vitest'
import {
  generatePlanForGap,
  loadTrainingGenerationContext,
  mutateReviewedTrainingPlan,
  normalizeEvidenceRefs,
  TrainingPlanChangedError,
  type TrainingApi,
} from '@/features/training/api/training-api'

function api(overrides: Partial<TrainingApi> = {}): TrainingApi {
  return {
    listSkillGapSnapshots: async () => ({ code: 0, data: { items: [{ snapshotId: 'gap-1' }] } }),
    getConfirmedMemoryProfile: async () => ({
      code: 0,
      data: [{ memoryId: 'm1', type: 'TIME_CONSTRAINT', normalizedKey: 'weekly_hours' }],
    }),
    listTrainingPlans: async () => ({ code: 0, data: { items: [] } }),
    generateTrainingPlan: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'PENDING_CONFIRMATION', version: 1 },
    }),
    getTrainingPlan: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'ACTIVE', version: 2 },
    }),
    confirmTrainingPlan: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'ACTIVE', version: 3 },
    }),
    completeTrainingPlan: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'COMPLETED', version: 3 },
    }),
    cancelTrainingPlan: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'CANCELLED', version: 3 },
    }),
    startTrainingPlanItem: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'ACTIVE', version: 3 },
    }),
    completeTrainingPlanItem: async () => ({
      code: 0,
      data: { planId: 'p1', status: 'ACTIVE', version: 3 },
    }),
    ...overrides,
  }
}

describe('training API workflow', () => {
  it('loads gap and confirmed planning inputs', async () => {
    const result = await loadTrainingGenerationContext(api())
    expect(result.gapSnapshots[0]?.snapshotId).toBe('gap-1')
    expect(result.confirmedMemories[0]?.normalizedKey).toBe('weekly_hours')
  })
  it('generates from the selected gap snapshot', async () => {
    const generate = vi.fn<TrainingApi['generateTrainingPlan']>(async () => ({
      code: 0,
      data: { planId: 'p1' },
    }))
    await generatePlanForGap('gap-1', api({ generateTrainingPlan: generate }))
    expect(generate).toHaveBeenCalledWith({ gapSnapshotId: 'gap-1' })
  })
  it('rereads the plan version before mutating', async () => {
    const start = vi.fn<TrainingApi['startTrainingPlanItem']>(async () => ({
      code: 0,
      data: { planId: 'p1', version: 3 },
    }))
    await mutateReviewedTrainingPlan(
      'start-item',
      { planId: 'p1', version: 2 },
      { itemId: 'i1' },
      api({ startTrainingPlanItem: start }),
    )
    expect(start).toHaveBeenCalledWith('p1', 'i1', { expectedVersion: 2 })
  })
  it('blocks stale plan mutations', async () => {
    const confirm = vi.fn<TrainingApi['confirmTrainingPlan']>()
    await expect(
      mutateReviewedTrainingPlan(
        'confirm',
        { planId: 'p1', version: 1 },
        {},
        api({ confirmTrainingPlan: confirm }),
      ),
    ).rejects.toBeInstanceOf(TrainingPlanChangedError)
    expect(confirm).not.toHaveBeenCalled()
  })
  it('normalizes and validates evidence references', () => {
    expect(
      normalizeEvidenceRefs([' github:repo/pr/1 ', 'github:repo/pr/1', 'test-report:unit/1']),
    ).toEqual(['github:repo/pr/1', 'test-report:unit/1'])
    expect(() => normalizeEvidenceRefs(['https://example.com'])).toThrow('证据格式')
  })
})
