import { describe, expect, it } from 'vitest'

import { loadDashboardSnapshot, type DashboardApi } from '@/features/dashboard/api/dashboard-api'
import { ApiError } from '@/shared/api/errors'

function createApi(overrides: Partial<DashboardApi> = {}): DashboardApi {
  return {
    getLatestTargetRole: async () => ({
      data: {
        targetRoleId: 'role-1',
        targetRoleVersion: 2,
        requirements: { jobTitle: 'AI Agent 应用开发' },
      },
    }),
    listSkillGapSnapshots: async () => ({
      data: { items: [{ snapshotId: 'gap-1', items: [] }] },
    }),
    listTrainingPlans: async () => ({
      data: { items: [{ planId: 'plan-1', title: '四周训练计划' }] },
    }),
    listPendingMemoryCandidates: async () => ({ data: [] }),
    listCoachingSessions: async () => ({ data: { items: [] } }),
    listInterviews: async () => ({ data: { items: [], nextCursor: null } }),
    ...overrides,
  }
}

describe('dashboard API projection', () => {
  it('loads six independent sections without copying them into Pinia', async () => {
    const snapshot = await loadDashboardSnapshot(createApi())

    expect(snapshot.targetRole.data?.requirements?.jobTitle).toBe('AI Agent 应用开发')
    expect(snapshot.skillGap.data?.snapshotId).toBe('gap-1')
    expect(snapshot.trainingPlan.data?.planId).toBe('plan-1')
    expect(snapshot.pendingMemories.data).toEqual([])
    expect(snapshot.coachingSession.data).toBeNull()
    expect(snapshot.mockInterview.data).toBeNull()
  })

  it('treats a missing latest target role as an empty first-use state', async () => {
    const snapshot = await loadDashboardSnapshot(
      createApi({
        getLatestTargetRole: async () => {
          throw new ApiError({ kind: 'not-found', message: '目标岗位不存在' })
        },
      }),
    )

    expect(snapshot.targetRole).toEqual({ data: null, error: null })
  })

  it('preserves successful sections when one request fails', async () => {
    const snapshot = await loadDashboardSnapshot(
      createApi({
        listTrainingPlans: async () => {
          throw new ApiError({ kind: 'unavailable', message: '训练计划服务暂时不可用' })
        },
      }),
    )

    expect(snapshot.targetRole.data?.targetRoleId).toBe('role-1')
    expect(snapshot.trainingPlan.data).toBeNull()
    expect(snapshot.trainingPlan.error).toBe('训练计划服务暂时不可用')
  })
})
