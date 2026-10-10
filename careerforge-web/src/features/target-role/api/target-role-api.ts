import {
  confirmTargetRoleDraft,
  createTargetRoleDraft,
  getLatestTargetRole,
  getTargetRoleDraft,
} from '@/shared/api/generated/careerforge'
import type {
  BaseResponseTargetRoleDraftResponse,
  BaseResponseTargetRoleResponse,
  ConfirmTargetRoleDraftRequest,
  CreateTargetRoleDraftRequest,
  TargetRoleDraftResponse,
  TargetRoleResponse,
} from '@/shared/api/generated/model'
import { ApiError, toApiError } from '@/shared/api/errors'

export interface TargetRoleApi {
  getLatestTargetRole: () => Promise<BaseResponseTargetRoleResponse>
  createTargetRoleDraft: (
    request: CreateTargetRoleDraftRequest,
  ) => Promise<BaseResponseTargetRoleDraftResponse>
  getTargetRoleDraft: (draftId: string) => Promise<BaseResponseTargetRoleDraftResponse>
  confirmTargetRoleDraft: (
    draftId: string,
    request: ConfirmTargetRoleDraftRequest,
  ) => Promise<BaseResponseTargetRoleResponse>
}

const defaultApi: TargetRoleApi = {
  getLatestTargetRole,
  createTargetRoleDraft,
  getTargetRoleDraft,
  confirmTargetRoleDraft,
}

export class TargetRoleDraftChangedError extends Error {
  constructor(
    message: string,
    readonly latestDraft: TargetRoleDraftResponse,
  ) {
    super(message)
    this.name = 'TargetRoleDraftChangedError'
  }
}

export async function loadLatestTargetRole(
  api: TargetRoleApi = defaultApi,
): Promise<TargetRoleResponse | null> {
  try {
    const response = await api.getLatestTargetRole()
    return requireData(response.data, '最新目标岗位响应缺少数据')
  } catch (error) {
    const apiError = toApiError(error)
    if (apiError.kind === 'not-found') {
      return null
    }
    throw apiError
  }
}

export async function submitTargetRoleDraft(
  input: CreateTargetRoleDraftRequest,
  api: TargetRoleApi = defaultApi,
): Promise<TargetRoleDraftResponse> {
  const response = await api.createTargetRoleDraft({
    sourceRef: input.sourceRef.trim(),
    jdText: input.jdText.trim(),
  })
  return requireData(response.data, '目标岗位草案响应缺少数据')
}

export async function reloadTargetRoleDraft(
  draftId: string,
  api: TargetRoleApi = defaultApi,
): Promise<TargetRoleDraftResponse> {
  const response = await api.getTargetRoleDraft(draftId)
  return requireData(response.data, '目标岗位草案响应缺少数据')
}

export async function confirmReviewedTargetRoleDraft(
  reviewedDraft: TargetRoleDraftResponse,
  api: TargetRoleApi = defaultApi,
): Promise<TargetRoleResponse> {
  const draftId = requireString(reviewedDraft.draftId, '草案 ID 缺失，请重新创建草案')
  const reviewedVersion = requireNumber(reviewedDraft.version, '草案版本缺失，请重新创建草案')
  const latestDraft = await reloadTargetRoleDraft(draftId, api)

  if (latestDraft.status !== 'PENDING') {
    throw new TargetRoleDraftChangedError('草案状态已经变化，请核对最新状态', latestDraft)
  }

  if (latestDraft.version !== reviewedVersion) {
    throw new TargetRoleDraftChangedError('草案版本已经变化，请重新审阅后确认', latestDraft)
  }

  const response = await api.confirmTargetRoleDraft(draftId, {
    expectedVersion: reviewedVersion,
  })
  return requireData(response.data, '目标岗位确认响应缺少数据')
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
