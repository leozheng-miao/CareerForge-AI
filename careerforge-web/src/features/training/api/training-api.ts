import {
  cancelTrainingPlan,
  completeTrainingPlan,
  completeTrainingPlanItem,
  confirmTrainingPlan,
  generateTrainingPlan,
  getConfirmedMemoryProfile,
  getTrainingPlan,
  listSkillGapSnapshots,
  listTrainingPlans,
  startTrainingPlanItem,
} from '@/shared/api/generated/careerforge'
import type {
  BaseResponseListMemoryCandidateResponse,
  BaseResponseSkillGapSnapshotPageResponse,
  BaseResponseTrainingPlanPageResponse,
  BaseResponseTrainingPlanResponse,
  CompleteTrainingPlanItemRequest,
  GenerateTrainingPlanRequest,
  ListSkillGapSnapshotsParams,
  ListTrainingPlansParams,
  MemoryCandidateResponse,
  SkillGapSnapshotResponse,
  TrainingPlanPageResponse,
  TrainingPlanResponse,
  TrainingPlanVersionRequest,
} from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/errors'

const TRAINING_GENERATION_TIMEOUT_MS = 100_000

export type TrainingPlanAction = 'confirm' | 'complete' | 'cancel' | 'start-item' | 'complete-item'

export interface TrainingApi {
  listSkillGapSnapshots: (
    params?: ListSkillGapSnapshotsParams,
  ) => Promise<BaseResponseSkillGapSnapshotPageResponse>
  getConfirmedMemoryProfile: () => Promise<BaseResponseListMemoryCandidateResponse>
  listTrainingPlans: (
    params?: ListTrainingPlansParams,
  ) => Promise<BaseResponseTrainingPlanPageResponse>
  generateTrainingPlan: (
    request: GenerateTrainingPlanRequest,
  ) => Promise<BaseResponseTrainingPlanResponse>
  getTrainingPlan: (planId: string) => Promise<BaseResponseTrainingPlanResponse>
  confirmTrainingPlan: (
    planId: string,
    request: TrainingPlanVersionRequest,
  ) => Promise<BaseResponseTrainingPlanResponse>
  completeTrainingPlan: (
    planId: string,
    request: TrainingPlanVersionRequest,
  ) => Promise<BaseResponseTrainingPlanResponse>
  cancelTrainingPlan: (
    planId: string,
    request: TrainingPlanVersionRequest,
  ) => Promise<BaseResponseTrainingPlanResponse>
  startTrainingPlanItem: (
    planId: string,
    itemId: string,
    request: TrainingPlanVersionRequest,
  ) => Promise<BaseResponseTrainingPlanResponse>
  completeTrainingPlanItem: (
    planId: string,
    itemId: string,
    request: CompleteTrainingPlanItemRequest,
  ) => Promise<BaseResponseTrainingPlanResponse>
}

export interface TrainingGenerationContext {
  gapSnapshots: SkillGapSnapshotResponse[]
  confirmedMemories: MemoryCandidateResponse[]
}

export interface TrainingPlanHistoryPage {
  items: TrainingPlanResponse[]
  nextCursor: string | null
  hasMore: boolean
}

export interface TrainingMutationOptions {
  itemId?: string
  evidenceRefs?: string[]
}

const defaultApi: TrainingApi = {
  listSkillGapSnapshots,
  getConfirmedMemoryProfile,
  listTrainingPlans,
  generateTrainingPlan: (request) =>
    generateTrainingPlan(request, { timeout: TRAINING_GENERATION_TIMEOUT_MS }),
  getTrainingPlan,
  confirmTrainingPlan,
  completeTrainingPlan,
  cancelTrainingPlan,
  startTrainingPlanItem,
  completeTrainingPlanItem,
}

export class TrainingPlanChangedError extends Error {
  constructor(
    message: string,
    readonly latestPlan: TrainingPlanResponse,
  ) {
    super(message)
    this.name = 'TrainingPlanChangedError'
  }
}

export async function loadTrainingGenerationContext(
  api: TrainingApi = defaultApi,
): Promise<TrainingGenerationContext> {
  const [gapResponse, memoryResponse] = await Promise.all([
    api.listSkillGapSnapshots({ limit: 20 }),
    api.getConfirmedMemoryProfile(),
  ])
  return {
    gapSnapshots: gapResponse.data?.items ?? [],
    confirmedMemories: memoryResponse.data ?? [],
  }
}

export async function loadTrainingPlanHistory(
  params: ListTrainingPlansParams = { limit: 10 },
  api: TrainingApi = defaultApi,
): Promise<TrainingPlanHistoryPage> {
  const response = await api.listTrainingPlans(params)
  const page: TrainingPlanPageResponse = requireData(response.data, '训练计划历史响应缺少数据')
  return {
    items: page.items ?? [],
    nextCursor: page.nextCursor ?? null,
    hasMore: Boolean(page.hasMore),
  }
}

export async function generatePlanForGap(
  gapSnapshotId: string,
  api: TrainingApi = defaultApi,
): Promise<TrainingPlanResponse> {
  if (!gapSnapshotId) {
    throw new ApiError({ kind: 'validation', message: '请选择能力差距快照' })
  }
  const response = await api.generateTrainingPlan({ gapSnapshotId })
  return requireData(response.data, '训练计划生成响应缺少数据')
}

export async function loadTrainingPlan(
  planId: string,
  api: TrainingApi = defaultApi,
): Promise<TrainingPlanResponse> {
  if (!planId) throw new ApiError({ kind: 'validation', message: '训练计划 ID 缺失' })
  const response = await api.getTrainingPlan(planId)
  return requireData(response.data, '训练计划响应缺少数据')
}

export async function mutateReviewedTrainingPlan(
  action: TrainingPlanAction,
  reviewedPlan: TrainingPlanResponse,
  options: TrainingMutationOptions = {},
  api: TrainingApi = defaultApi,
): Promise<TrainingPlanResponse> {
  const planId = requireString(reviewedPlan.planId, '训练计划 ID 缺失，请返回列表重试')
  const reviewedVersion = requireNumber(reviewedPlan.version, '训练计划版本缺失，请刷新后重试')
  const latestPlan = await loadTrainingPlan(planId, api)

  if (latestPlan.version !== reviewedVersion) {
    throw new TrainingPlanChangedError('训练计划版本已经变化，请核对最新状态后重试', latestPlan)
  }

  const request = { expectedVersion: reviewedVersion }
  let response: BaseResponseTrainingPlanResponse
  if (action === 'confirm') response = await api.confirmTrainingPlan(planId, request)
  else if (action === 'complete') response = await api.completeTrainingPlan(planId, request)
  else if (action === 'cancel') response = await api.cancelTrainingPlan(planId, request)
  else {
    const itemId = requireString(options.itemId, '训练项 ID 缺失，请刷新后重试')
    if (action === 'start-item') {
      response = await api.startTrainingPlanItem(planId, itemId, request)
    } else {
      const evidenceRefs = normalizeEvidenceRefs(options.evidenceRefs ?? [])
      response = await api.completeTrainingPlanItem(planId, itemId, {
        expectedVersion: reviewedVersion,
        evidenceRefs,
      })
    }
  }
  return requireData(response.data, '训练计划状态响应缺少数据')
}

export function normalizeEvidenceRefs(values: string[]) {
  const normalized = [...new Set(values.map((value) => value.trim()).filter(Boolean))]
  if (normalized.length < 1 || normalized.length > 20) {
    throw new ApiError({ kind: 'validation', message: '请提交 1 至 20 条完成证据引用' })
  }
  const pattern = /^(github|test-report):[A-Za-z0-9][A-Za-z0-9._/-]{0,180}$/
  if (normalized.some((value) => !pattern.test(value))) {
    throw new ApiError({
      kind: 'validation',
      message: '证据格式仅支持 github:路径 或 test-report:路径',
    })
  }
  return normalized
}

function requireData<T>(data: T | undefined, message: string): T {
  if (data === undefined) throw new ApiError({ kind: 'unknown', message })
  return data
}

function requireString(value: string | undefined, message: string): string {
  if (!value) throw new ApiError({ kind: 'unknown', message })
  return value
}

function requireNumber(value: number | undefined, message: string): number {
  if (value === undefined) throw new ApiError({ kind: 'unknown', message })
  return value
}
