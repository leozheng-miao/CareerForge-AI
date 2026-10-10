import {
  generateSkillGapSnapshot,
  getConfirmedSkillProfile,
  getLatestTargetRole,
  listSkillGapSnapshots,
} from '@/shared/api/generated/careerforge'
import type {
  BaseResponseConfirmedSkillProfileResponse,
  BaseResponseSkillGapSnapshotPageResponse,
  BaseResponseSkillGapSnapshotResponse,
  BaseResponseTargetRoleResponse,
  ConfirmedSkillProfileResponse,
  GenerateSkillGapSnapshotRequest,
  ListSkillGapSnapshotsParams,
  SkillGapSnapshotResponse,
  TargetRoleResponse,
} from '@/shared/api/generated/model'
import { ApiError, toApiError } from '@/shared/api/errors'

export interface SkillGapApi {
  getLatestTargetRole: () => Promise<BaseResponseTargetRoleResponse>
  getConfirmedSkillProfile: () => Promise<BaseResponseConfirmedSkillProfileResponse>
  listSkillGapSnapshots: (
    params?: ListSkillGapSnapshotsParams,
  ) => Promise<BaseResponseSkillGapSnapshotPageResponse>
  generateSkillGapSnapshot: (
    request: GenerateSkillGapSnapshotRequest,
  ) => Promise<BaseResponseSkillGapSnapshotResponse>
}

export interface SkillGapInputs {
  targetRole: TargetRoleResponse | null
  skillProfile: ConfirmedSkillProfileResponse
}

export interface SkillGapHistoryPage {
  items: SkillGapSnapshotResponse[]
  nextCursor: string | null
  hasMore: boolean
}

const defaultApi: SkillGapApi = {
  getLatestTargetRole,
  getConfirmedSkillProfile,
  listSkillGapSnapshots,
  generateSkillGapSnapshot,
}

export class SkillGapInputsChangedError extends Error {
  constructor(
    message: string,
    readonly latestInputs: SkillGapInputs,
  ) {
    super(message)
    this.name = 'SkillGapInputsChangedError'
  }
}

export async function loadSkillGapInputs(api: SkillGapApi = defaultApi): Promise<SkillGapInputs> {
  const [targetRole, profileResponse] = await Promise.all([
    loadLatestTargetRole(api),
    api.getConfirmedSkillProfile(),
  ])

  return {
    targetRole,
    skillProfile: requireData(profileResponse.data, '技能画像响应缺少数据'),
  }
}

export async function loadSkillGapHistory(
  cursor?: string,
  limit = 10,
  api: SkillGapApi = defaultApi,
): Promise<SkillGapHistoryPage> {
  const response = await api.listSkillGapSnapshots({ cursor, limit })
  const page = requireData(response.data, '能力差距历史响应缺少数据')
  return {
    items: page.items ?? [],
    nextCursor: page.nextCursor ?? null,
    hasMore: Boolean(page.hasMore),
  }
}

export async function generateSkillGapFromReviewedInputs(
  reviewedInputs: SkillGapInputs,
  api: SkillGapApi = defaultApi,
): Promise<SkillGapSnapshotResponse> {
  const reviewedTarget = requireTarget(reviewedInputs.targetRole)
  const reviewedTargetId = requireString(
    reviewedTarget.targetRoleId,
    '目标岗位 ID 缺失，请重新确认目标岗位',
  )
  const reviewedTargetVersion = requireNumber(
    reviewedTarget.targetRoleVersion,
    '目标岗位版本缺失，请重新确认目标岗位',
  )
  const reviewedProfileVersion = requireNumber(
    reviewedInputs.skillProfile.profileVersion,
    '技能画像版本缺失，请刷新后重试',
  )

  const latestInputs = await loadSkillGapInputs(api)
  const latestTarget = latestInputs.targetRole
  if (
    latestTarget?.targetRoleId !== reviewedTargetId ||
    latestTarget.targetRoleVersion !== reviewedTargetVersion ||
    latestInputs.skillProfile.profileVersion !== reviewedProfileVersion
  ) {
    throw new SkillGapInputsChangedError(
      '目标岗位或技能画像版本已经变化，请核对最新输入后重新生成',
      latestInputs,
    )
  }

  const response = await api.generateSkillGapSnapshot({
    targetRoleId: reviewedTargetId,
    expectedTargetRoleVersion: reviewedTargetVersion,
    expectedProfileVersion: reviewedProfileVersion,
  })
  return requireData(response.data, '能力差距快照响应缺少数据')
}

async function loadLatestTargetRole(api: SkillGapApi): Promise<TargetRoleResponse | null> {
  try {
    const response = await api.getLatestTargetRole()
    return requireData(response.data, '最新目标岗位响应缺少数据')
  } catch (error) {
    const apiError = toApiError(error)
    if (apiError.kind === 'not-found') return null
    throw apiError
  }
}

function requireTarget(target: TargetRoleResponse | null): TargetRoleResponse {
  if (!target) {
    throw new ApiError({ kind: 'validation', message: '请先确认目标岗位' })
  }
  return target
}

function requireData<T>(data: T | undefined, message: string): T {
  if (data === undefined) {
    throw new ApiError({ kind: 'unknown', message })
  }
  return data
}

function requireString(value: string | undefined, message: string): string {
  if (!value) {
    throw new ApiError({ kind: 'unknown', message })
  }
  return value
}

function requireNumber(value: number | undefined, message: string): number {
  if (value === undefined) {
    throw new ApiError({ kind: 'unknown', message })
  }
  return value
}
