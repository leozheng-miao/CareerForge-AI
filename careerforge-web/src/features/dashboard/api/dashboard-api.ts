import {
  getLatestTargetRole,
  listCoachingSessions,
  listPendingMemoryCandidates,
  listSkillGapSnapshots,
  listTrainingPlans,
} from '@/shared/api/generated/careerforge'
import type {
  CoachingSessionPageResponse,
  CoachingSessionResponse,
  MemoryCandidateResponse,
  SkillGapSnapshotPageResponse,
  SkillGapSnapshotResponse,
  TargetRoleResponse,
  TrainingPlanPageResponse,
  TrainingPlanResponse,
} from '@/shared/api/generated/model'
import { listInterviews, type InterviewPage, type InterviewRecord } from '@/features/interview/api/interview-flow-api'
import { toApiError } from '@/shared/api/errors'

interface Envelope<T> {
  data?: T
}

export interface DashboardApi {
  getLatestTargetRole: () => Promise<Envelope<TargetRoleResponse>>
  listSkillGapSnapshots: (params: {
    limit: number
  }) => Promise<Envelope<SkillGapSnapshotPageResponse>>
  listTrainingPlans: (params: { limit: number }) => Promise<Envelope<TrainingPlanPageResponse>>
  listPendingMemoryCandidates: () => Promise<Envelope<MemoryCandidateResponse[]>>
  listCoachingSessions: (params: {
    limit: number
  }) => Promise<Envelope<CoachingSessionPageResponse>>
  listInterviews: (params: { limit: number }) => Promise<Envelope<InterviewPage>>
}

export interface DashboardSection<T> {
  data: T
  error: string | null
}

export interface DashboardSnapshot {
  targetRole: DashboardSection<TargetRoleResponse | null>
  skillGap: DashboardSection<SkillGapSnapshotResponse | null>
  trainingPlan: DashboardSection<TrainingPlanResponse | null>
  pendingMemories: DashboardSection<MemoryCandidateResponse[]>
  coachingSession: DashboardSection<CoachingSessionResponse | null>
  mockInterview: DashboardSection<InterviewRecord | null>
  loadedAt: string
}

const defaultApi: DashboardApi = {
  getLatestTargetRole,
  listSkillGapSnapshots,
  listTrainingPlans,
  listPendingMemoryCandidates,
  listCoachingSessions,
  listInterviews: async ({ limit }) => ({ data: await listInterviews(undefined, limit) }),
}

export async function loadDashboardSnapshot(
  api: DashboardApi = defaultApi,
): Promise<DashboardSnapshot> {
  const [targetRole, skillGap, trainingPlan, pendingMemories, coachingSession, mockInterview] =
    await Promise.all([
      loadSection(
        () => api.getLatestTargetRole(),
        (data) => data ?? null,
        null,
      ),
      loadSection(() => api.listSkillGapSnapshots({ limit: 1 }), firstItem, null),
      loadSection(() => api.listTrainingPlans({ limit: 1 }), firstItem, null),
      loadSection(
        () => api.listPendingMemoryCandidates(),
        (data) => data ?? [],
        [],
      ),
      loadSection(() => api.listCoachingSessions({ limit: 1 }), firstItem, null),
      loadSection(() => api.listInterviews({ limit: 1 }), firstItem, null),
    ])

  return {
    targetRole,
    skillGap,
    trainingPlan,
    pendingMemories,
    coachingSession,
    mockInterview,
    loadedAt: new Date().toISOString(),
  }
}

async function loadSection<TResponse, TData>(
  request: () => Promise<Envelope<TResponse>>,
  select: (data: TResponse | undefined) => TData,
  emptyValue: TData,
): Promise<DashboardSection<TData>> {
  try {
    const response = await request()
    return { data: select(response.data), error: null }
  } catch (error) {
    const apiError = toApiError(error)
    if (apiError.kind === 'not-found') {
      return { data: emptyValue, error: null }
    }
    return { data: emptyValue, error: apiError.message }
  }
}

function firstItem<T>(data: { items?: T[] } | undefined) {
  return data?.items?.[0] ?? null
}
