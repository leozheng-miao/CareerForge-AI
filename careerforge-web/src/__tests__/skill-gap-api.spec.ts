import { describe, expect, it, vi } from 'vitest'

import {
  generateSkillGapFromReviewedInputs,
  loadSkillGapHistory,
  loadSkillGapInputs,
  SkillGapInputsChangedError,
  type SkillGapApi,
} from '@/features/skill-gap/api/skill-gap-api'
import { ApiError } from '@/shared/api/errors'

function createApi(overrides: Partial<SkillGapApi> = {}): SkillGapApi {
  return {
    getLatestTargetRole: async () => ({
      code: 0,
      data: { targetRoleId: 'role-1', targetRoleVersion: 2 },
    }),
    getConfirmedSkillProfile: async () => ({
      code: 0,
      data: { profileVersion: 3, skillEvidence: [] },
    }),
    listSkillGapSnapshots: async () => ({
      code: 0,
      data: { items: [], hasMore: false },
    }),
    generateSkillGapSnapshot: async () => ({
      code: 0,
      data: {
        snapshotId: 'snapshot-1',
        targetRoleId: 'role-1',
        targetRoleVersion: 2,
        profileVersion: 3,
        items: [],
      },
    }),
    ...overrides,
  }
}

describe('skill gap API workflow', () => {
  it('loads an empty target role as a valid first-use input state', async () => {
    const inputs = await loadSkillGapInputs(
      createApi({
        getLatestTargetRole: async () => {
          throw new ApiError({ kind: 'not-found', message: '目标岗位不存在' })
        },
      }),
    )

    expect(inputs.targetRole).toBeNull()
    expect(inputs.skillProfile.profileVersion).toBe(3)
  })

  it('preserves the opaque cursor when loading snapshot history', async () => {
    const list = vi.fn<SkillGapApi['listSkillGapSnapshots']>(async () => ({
      code: 0,
      data: { items: [{ snapshotId: 'snapshot-2' }], nextCursor: 'opaque-cursor', hasMore: true },
    }))

    const page = await loadSkillGapHistory(
      'previous-cursor',
      10,
      createApi({ listSkillGapSnapshots: list }),
    )

    expect(list).toHaveBeenCalledWith({ cursor: 'previous-cursor', limit: 10 })
    expect(page.nextCursor).toBe('opaque-cursor')
    expect(page.hasMore).toBe(true)
  })

  it('rereads both input versions before generating a snapshot', async () => {
    const generate = vi.fn<SkillGapApi['generateSkillGapSnapshot']>(async (request) => ({
      code: 0,
      data: {
        snapshotId: 'snapshot-1',
        ...request,
        profileVersion: request.expectedProfileVersion,
      },
    }))
    const api = createApi({ generateSkillGapSnapshot: generate })
    const inputs = await loadSkillGapInputs(api)

    const snapshot = await generateSkillGapFromReviewedInputs(inputs, api)

    expect(generate).toHaveBeenCalledWith({
      targetRoleId: 'role-1',
      expectedTargetRoleVersion: 2,
      expectedProfileVersion: 3,
    })
    expect(snapshot.snapshotId).toBe('snapshot-1')
  })

  it('does not generate when the profile version changed', async () => {
    const generate = vi.fn<SkillGapApi['generateSkillGapSnapshot']>()
    const api = createApi({
      getConfirmedSkillProfile: async () => ({
        code: 0,
        data: { profileVersion: 4, skillEvidence: [] },
      }),
      generateSkillGapSnapshot: generate,
    })

    await expect(
      generateSkillGapFromReviewedInputs(
        {
          targetRole: { targetRoleId: 'role-1', targetRoleVersion: 2 },
          skillProfile: { profileVersion: 3, skillEvidence: [] },
        },
        api,
      ),
    ).rejects.toBeInstanceOf(SkillGapInputsChangedError)
    expect(generate).not.toHaveBeenCalled()
  })
})
