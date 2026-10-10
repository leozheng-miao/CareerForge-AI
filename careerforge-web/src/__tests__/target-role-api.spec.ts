import { describe, expect, it, vi } from 'vitest'

import {
  confirmReviewedTargetRoleDraft,
  loadLatestTargetRole,
  submitTargetRoleDraft,
  TargetRoleDraftChangedError,
  type TargetRoleApi,
} from '@/features/target-role/api/target-role-api'
import { ApiError } from '@/shared/api/errors'

function createApi(overrides: Partial<TargetRoleApi> = {}): TargetRoleApi {
  return {
    getLatestTargetRole: async () => ({
      code: 0,
      data: { targetRoleId: 'role-1', targetRoleVersion: 2 },
    }),
    createTargetRoleDraft: async () => ({
      code: 0,
      data: { draftId: 'draft-1', version: 0, status: 'PENDING' },
    }),
    getTargetRoleDraft: async () => ({
      code: 0,
      data: { draftId: 'draft-1', version: 0, status: 'PENDING' },
    }),
    confirmTargetRoleDraft: async () => ({
      code: 0,
      data: { targetRoleId: 'role-2', targetRoleVersion: 3 },
    }),
    ...overrides,
  }
}

describe('target role API workflow', () => {
  it('treats a missing latest target role as the first-use state', async () => {
    const result = await loadLatestTargetRole(
      createApi({
        getLatestTargetRole: async () => {
          throw new ApiError({ kind: 'not-found', message: '目标岗位不存在' })
        },
      }),
    )

    expect(result).toBeNull()
  })

  it('normalizes user input before creating a server-side draft', async () => {
    const create = vi.fn<TargetRoleApi['createTargetRoleDraft']>(async () => ({
      code: 0,
      data: { draftId: 'draft-1', version: 0, status: 'PENDING' as const },
    }))

    await submitTargetRoleDraft(
      { sourceRef: '  招聘网站  ', jdText: '  Java Agent 岗位要求  ' },
      createApi({ createTargetRoleDraft: create }),
    )

    expect(create).toHaveBeenCalledWith({
      sourceRef: '招聘网站',
      jdText: 'Java Agent 岗位要求',
    })
  })

  it('rereads the draft and confirms the reviewed version', async () => {
    const confirm = vi.fn<TargetRoleApi['confirmTargetRoleDraft']>(
      async (_draftId: string, request: { expectedVersion: number }) => ({
        code: 0,
        data: { targetRoleId: 'role-2', targetRoleVersion: request.expectedVersion + 1 },
      }),
    )
    const api = createApi({ confirmTargetRoleDraft: confirm })

    const result = await confirmReviewedTargetRoleDraft(
      { draftId: 'draft-1', version: 0, status: 'PENDING' },
      api,
    )

    expect(confirm).toHaveBeenCalledWith('draft-1', { expectedVersion: 0 })
    expect(result.targetRoleVersion).toBe(1)
  })

  it('does not confirm when the server draft version changed', async () => {
    const confirm = vi.fn<TargetRoleApi['confirmTargetRoleDraft']>()
    const api = createApi({
      getTargetRoleDraft: async () => ({
        code: 0,
        data: { draftId: 'draft-1', version: 1, status: 'PENDING' },
      }),
      confirmTargetRoleDraft: confirm,
    })

    await expect(
      confirmReviewedTargetRoleDraft({ draftId: 'draft-1', version: 0, status: 'PENDING' }, api),
    ).rejects.toBeInstanceOf(TargetRoleDraftChangedError)
    expect(confirm).not.toHaveBeenCalled()
  })
})
