import {
  createPersonalEvidenceArtifact,
  getPersonalEvidenceArtifact,
  listPersonalEvidenceArtifacts,
  revokePersonalEvidenceArtifact,
  updatePersonalEvidenceArtifact,
} from '@/shared/api/generated/careerforge'
import type {
  CreatePersonalEvidenceRequest,
  ListPersonalEvidenceArtifactsParams,
  PersonalEvidenceResponse,
} from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/errors'

export class EvidenceChangedError extends Error {
  constructor(readonly latest: PersonalEvidenceResponse) {
    super('证据版本已经变化，请核对最新内容后重试')
    this.name = 'EvidenceChangedError'
  }
}

export async function loadEvidencePage(
  params: ListPersonalEvidenceArtifactsParams = { limit: 12 },
) {
  const response = await listPersonalEvidenceArtifacts(params)
  return {
    items: response.data?.items ?? [],
    nextCursor: response.data?.nextCursor ?? null,
    hasMore: Boolean(response.data?.hasMore),
  }
}

export async function loadEvidence(artifactId: string) {
  const response = await getPersonalEvidenceArtifact(artifactId)
  return requireData(response.data, '证据详情响应缺少数据')
}

export async function createEvidence(request: CreatePersonalEvidenceRequest) {
  validateEvidence(request.sourceName, request.rawContent)
  const response = await createPersonalEvidenceArtifact({
    ...request,
    sourceName: request.sourceName.trim(),
    rawContent: request.rawContent.trim(),
  })
  return requireData(response.data, '证据创建响应缺少数据')
}

export async function updateReviewedEvidence(
  reviewed: PersonalEvidenceResponse,
  sourceName: string,
  rawContent: string,
) {
  validateEvidence(sourceName, rawContent)
  const artifactId = requireString(reviewed.artifactId, '证据 ID 缺失')
  const expectedVersion = requireNumber(reviewed.artifactVersion, '证据版本缺失')
  const latest = await loadEvidence(artifactId)
  if (latest.artifactVersion !== expectedVersion) throw new EvidenceChangedError(latest)
  const response = await updatePersonalEvidenceArtifact(artifactId, {
    expectedVersion,
    sourceName: sourceName.trim(),
    rawContent: rawContent.trim(),
  })
  return requireData(response.data, '证据更新响应缺少数据')
}

export async function revokeReviewedEvidence(reviewed: PersonalEvidenceResponse) {
  const artifactId = requireString(reviewed.artifactId, '证据 ID 缺失')
  const expectedVersion = requireNumber(reviewed.artifactVersion, '证据版本缺失')
  const latest = await loadEvidence(artifactId)
  if (latest.artifactVersion !== expectedVersion) throw new EvidenceChangedError(latest)
  const response = await revokePersonalEvidenceArtifact(artifactId, { expectedVersion })
  return requireData(response.data, '证据撤销响应缺少数据')
}

function validateEvidence(sourceName: string, rawContent: string) {
  if (!sourceName.trim()) throw new ApiError({ kind: 'validation', message: '请输入证据来源名称' })
  if (!rawContent.trim())
    throw new ApiError({ kind: 'validation', message: '请输入文本或 Markdown 证据' })
  if (rawContent.length > 100_000)
    throw new ApiError({ kind: 'validation', message: '证据内容不能超过 100000 字符' })
}

function requireData<T>(data: T | undefined, message: string): T {
  if (data === undefined) throw new ApiError({ kind: 'unknown', message })
  return data
}

function requireString(value: string | undefined, message: string) {
  if (!value) throw new ApiError({ kind: 'unknown', message })
  return value
}

function requireNumber(value: number | undefined, message: string) {
  if (value === undefined) throw new ApiError({ kind: 'unknown', message })
  return value
}
