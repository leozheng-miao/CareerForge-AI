import {
  confirmMemory,
  confirmMemoryReplacement,
  getConfirmedMemoryProfile,
  getConfirmedSkillProfile,
  listPendingMemoryCandidates,
  rejectMemory,
  revokeMemory,
} from '@/shared/api/generated/careerforge'
import type {
  BaseResponseConfirmedSkillProfileResponse,
  BaseResponseListMemoryCandidateResponse,
  BaseResponseMemoryCandidateResponse,
  ConfirmedSkillProfileResponse,
  MemoryCandidateResponse,
  MemoryDecisionRequest,
  MemoryReplacementDecisionRequest,
} from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/errors'

export type MemoryDecisionAction = 'confirm' | 'reject' | 'revoke'

export interface MemoryApi {
  listPendingMemoryCandidates: () => Promise<BaseResponseListMemoryCandidateResponse>
  getConfirmedMemoryProfile: () => Promise<BaseResponseListMemoryCandidateResponse>
  getConfirmedSkillProfile: () => Promise<BaseResponseConfirmedSkillProfileResponse>
  confirmMemory: (
    memoryId: string,
    request: MemoryDecisionRequest,
  ) => Promise<BaseResponseMemoryCandidateResponse>
  rejectMemory: (
    memoryId: string,
    request: MemoryDecisionRequest,
  ) => Promise<BaseResponseMemoryCandidateResponse>
  revokeMemory: (
    memoryId: string,
    request: MemoryDecisionRequest,
  ) => Promise<BaseResponseMemoryCandidateResponse>
  confirmMemoryReplacement: (
    existingMemoryId: string,
    request: MemoryReplacementDecisionRequest,
  ) => Promise<BaseResponseMemoryCandidateResponse>
}

export interface MemoryWorkspace {
  pending: MemoryCandidateResponse[]
  confirmed: MemoryCandidateResponse[]
  skillProfile: ConfirmedSkillProfileResponse
}

const defaultApi: MemoryApi = {
  listPendingMemoryCandidates,
  getConfirmedMemoryProfile,
  getConfirmedSkillProfile,
  confirmMemory,
  rejectMemory,
  revokeMemory,
  confirmMemoryReplacement,
}

export class MemoryStateChangedError extends Error {
  constructor(
    message: string,
    readonly latestWorkspace: MemoryWorkspace,
  ) {
    super(message)
    this.name = 'MemoryStateChangedError'
  }
}

export async function loadMemoryWorkspace(api: MemoryApi = defaultApi): Promise<MemoryWorkspace> {
  const [pendingResponse, confirmedResponse, skillProfileResponse] = await Promise.all([
    api.listPendingMemoryCandidates(),
    api.getConfirmedMemoryProfile(),
    api.getConfirmedSkillProfile(),
  ])

  return {
    pending: pendingResponse.data ?? [],
    confirmed: confirmedResponse.data ?? [],
    skillProfile: requireData(skillProfileResponse.data, '技能画像响应缺少数据'),
  }
}

export async function applyMemoryDecision(
  action: MemoryDecisionAction,
  reviewedMemory: MemoryCandidateResponse,
  note: string,
  api: MemoryApi = defaultApi,
): Promise<MemoryCandidateResponse> {
  const memoryId = requireString(reviewedMemory.memoryId, 'Memory ID 缺失，请刷新后重试')
  const reviewedVersion = requireNumber(reviewedMemory.version, 'Memory 版本缺失，请刷新后重试')
  const latestWorkspace = await loadMemoryWorkspace(api)
  const collection = action === 'revoke' ? latestWorkspace.confirmed : latestWorkspace.pending
  const latestMemory = collection.find((memory) => memory.memoryId === memoryId)

  if (!latestMemory || latestMemory.version !== reviewedVersion) {
    throw new MemoryStateChangedError(
      'Memory 状态或版本已经变化，请核对最新列表后重试',
      latestWorkspace,
    )
  }

  const expectedVersion = requireNumber(latestMemory.version, 'Memory 版本缺失，请刷新后重试')
  const request = decisionRequest(expectedVersion, note)

  if (action === 'reject') {
    const response = await api.rejectMemory(memoryId, request)
    return requireData(response.data, 'Memory 拒绝响应缺少数据')
  }

  if (action === 'revoke') {
    const response = await api.revokeMemory(memoryId, request)
    return requireData(response.data, 'Memory 撤销响应缺少数据')
  }

  if (latestMemory.supersedesId) {
    const existingMemory = latestWorkspace.confirmed.find(
      (memory) => memory.memoryId === latestMemory.supersedesId,
    )
    if (!existingMemory) {
      throw new ApiError({
        kind: 'validation',
        message: '被替代的旧 Memory 不在当前有效画像中，请刷新后重试',
      })
    }
    const existingMemoryId = requireString(
      existingMemory.memoryId,
      '旧 Memory ID 缺失，请刷新后重试',
    )
    const replacementRequest: MemoryReplacementDecisionRequest = {
      replacementMemoryId: memoryId,
      expectedExistingVersion: requireNumber(
        existingMemory.version,
        '旧 Memory 版本缺失，请刷新后重试',
      ),
      expectedReplacementVersion: expectedVersion,
      note: normalizeNote(note),
    }
    const response = await api.confirmMemoryReplacement(existingMemoryId, replacementRequest)
    return requireData(response.data, 'Memory 替代响应缺少数据')
  }

  const response = await api.confirmMemory(memoryId, request)
  return requireData(response.data, 'Memory 确认响应缺少数据')
}

function decisionRequest(expectedVersion: number, note: string): MemoryDecisionRequest {
  return {
    expectedVersion,
    note: normalizeNote(note),
  }
}

function normalizeNote(note: string) {
  return note.trim() || undefined
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
