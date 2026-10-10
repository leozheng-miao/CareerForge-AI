import { describe, expect, it, vi } from 'vitest'

import {
  applyMemoryDecision,
  loadMemoryWorkspace,
  MemoryStateChangedError,
  type MemoryApi,
} from '@/features/memory/api/memory-api'

function createApi(overrides: Partial<MemoryApi> = {}): MemoryApi {
  return {
    listPendingMemoryCandidates: async () => ({
      code: 0,
      data: [
        {
          memoryId: 'pending-1',
          type: 'TIME_CONSTRAINT',
          status: 'PENDING',
          sourceType: 'CONVERSATION_TURN',
          version: 0,
        },
      ],
    }),
    getConfirmedMemoryProfile: async () => ({ code: 0, data: [] }),
    getConfirmedSkillProfile: async () => ({
      code: 0,
      data: { profileVersion: 2, skillEvidence: [] },
    }),
    confirmMemory: async () => ({
      code: 0,
      data: { memoryId: 'pending-1', status: 'CONFIRMED', version: 1 },
    }),
    rejectMemory: async () => ({
      code: 0,
      data: { memoryId: 'pending-1', status: 'REJECTED', version: 1 },
    }),
    revokeMemory: async () => ({
      code: 0,
      data: { memoryId: 'confirmed-1', status: 'REVOKED', version: 2 },
    }),
    confirmMemoryReplacement: async () => ({
      code: 0,
      data: { memoryId: 'pending-1', status: 'CONFIRMED', version: 1 },
    }),
    ...overrides,
  }
}

describe('memory decision workflow', () => {
  it('loads pending, confirmed and skill profile snapshots together', async () => {
    const workspace = await loadMemoryWorkspace(createApi())

    expect(workspace.pending).toHaveLength(1)
    expect(workspace.confirmed).toEqual([])
    expect(workspace.skillProfile.profileVersion).toBe(2)
  })

  it('rereads the candidate and submits its current version', async () => {
    const confirm = vi.fn<MemoryApi['confirmMemory']>(async () => ({
      code: 0,
      data: { memoryId: 'pending-1', status: 'CONFIRMED', version: 1 },
    }))
    const api = createApi({ confirmMemory: confirm })

    await applyMemoryDecision(
      'confirm',
      {
        memoryId: 'pending-1',
        status: 'PENDING',
        sourceType: 'CONVERSATION_TURN',
        version: 0,
      },
      '  用户已核对  ',
      api,
    )

    expect(confirm).toHaveBeenCalledWith('pending-1', {
      expectedVersion: 0,
      note: '用户已核对',
    })
  })

  it('uses the atomic replacement endpoint for a superseding candidate', async () => {
    const replacement = vi.fn<MemoryApi['confirmMemoryReplacement']>(async () => ({
      code: 0,
      data: { memoryId: 'pending-1', status: 'CONFIRMED', version: 1 },
    }))
    const api = createApi({
      listPendingMemoryCandidates: async () => ({
        code: 0,
        data: [
          {
            memoryId: 'pending-1',
            status: 'PENDING',
            supersedesId: 'confirmed-1',
            version: 0,
          },
        ],
      }),
      getConfirmedMemoryProfile: async () => ({
        code: 0,
        data: [{ memoryId: 'confirmed-1', status: 'CONFIRMED', version: 3 }],
      }),
      confirmMemoryReplacement: replacement,
    })

    await applyMemoryDecision(
      'confirm',
      { memoryId: 'pending-1', status: 'PENDING', supersedesId: 'confirmed-1', version: 0 },
      '',
      api,
    )

    expect(replacement).toHaveBeenCalledWith('confirmed-1', {
      replacementMemoryId: 'pending-1',
      expectedExistingVersion: 3,
      expectedReplacementVersion: 0,
      note: undefined,
    })
  })

  it('does not submit a decision when the version changed', async () => {
    const confirm = vi.fn<MemoryApi['confirmMemory']>()
    const api = createApi({
      listPendingMemoryCandidates: async () => ({
        code: 0,
        data: [{ memoryId: 'pending-1', status: 'PENDING', version: 1 }],
      }),
      confirmMemory: confirm,
    })

    await expect(
      applyMemoryDecision(
        'confirm',
        { memoryId: 'pending-1', status: 'PENDING', version: 0 },
        '',
        api,
      ),
    ).rejects.toBeInstanceOf(MemoryStateChangedError)
    expect(confirm).not.toHaveBeenCalled()
  })
})
