import {
  createCoachingRun,
  createCoachingSession,
  extractMemoryCandidates,
  getCoachingRun,
  getCoachingSession,
  listCoachingRuns,
  listCoachingSessions,
  listCoachingTurns,
} from '@/shared/api/generated/careerforge'
import type {
  CoachingRunResponse,
  CoachingSessionResponse,
  CoachingTurnResponse,
} from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/errors'

export async function loadCoachSessions(cursor?: string) {
  const response = await listCoachingSessions({ limit: 12, cursor })
  return {
    items: response.data?.items ?? [],
    nextCursor: response.data?.nextCursor ?? null,
    hasMore: Boolean(response.data?.hasMore),
  }
}

export async function createCoachSession(title: string) {
  const normalized = title.trim()
  if (!normalized) throw new ApiError({ kind: 'validation', message: '请输入会话标题' })
  const response = await createCoachingSession({ title: normalized })
  return requireData(response.data, '会话创建响应缺少数据')
}

export async function loadCoachWorkspace(sessionId: string) {
  const [sessionResponse, turnsResponse, runsResponse] = await Promise.all([
    getCoachingSession(sessionId),
    listCoachingTurns(sessionId, { limit: 50 }),
    listCoachingRuns({ sessionId, limit: 20 }),
  ])
  return {
    session: requireData(sessionResponse.data, '会话响应缺少数据'),
    turns: turnsResponse.data?.items ?? [],
    turnsCursor: turnsResponse.data?.nextCursor ?? null,
    hasOlderTurns: Boolean(turnsResponse.data?.hasMore),
    runs: runsResponse.data?.items ?? [],
  }
}

export async function submitCoachRun(
  session: CoachingSessionResponse,
  message: string,
  requestId: string = crypto.randomUUID(),
) {
  const sessionId = requireString(session.sessionId, '会话 ID 缺失')
  const expectedSessionVersion = requireNumber(session.version, '会话版本缺失')
  const normalized = message.trim()
  if (!normalized) throw new ApiError({ kind: 'validation', message: '请输入想咨询的内容' })
  if (normalized.length > 8_000) {
    throw new ApiError({ kind: 'validation', message: '消息不能超过 8000 个字符' })
  }
  const response = await createCoachingRun({
    sessionId,
    requestId,
    expectedSessionVersion,
    message: normalized,
  })
  return requireData(response.data, 'Run 提交响应缺少数据')
}

export async function loadCoachRun(runId: string): Promise<CoachingRunResponse> {
  const response = await getCoachingRun(runId)
  return requireData(response.data, 'Run 响应缺少数据')
}

export async function loadOlderCoachTurns(sessionId: string, cursor: string) {
  const response = await listCoachingTurns(sessionId, { cursor, limit: 50 })
  return {
    items: response.data?.items ?? [],
    nextCursor: response.data?.nextCursor ?? null,
    hasMore: Boolean(response.data?.hasMore),
  }
}

export async function extractMemoriesFromTurns(sessionId: string, turns: CoachingTurnResponse[]) {
  const turnIds = turns
    .filter((turn) => turn.memoryExtractionEligible && turn.turnId)
    .map((turn) => turn.turnId as string)
  if (turnIds.length === 0) {
    throw new ApiError({ kind: 'validation', message: '当前没有可用于记忆提取的已完成对话' })
  }
  const response = await extractMemoryCandidates(sessionId, { turnIds: turnIds.slice(-20) })
  return response.data ?? []
}

export function mergeTurns(...groups: CoachingTurnResponse[][]) {
  const byId = new Map<string, CoachingTurnResponse>()
  groups.flat().forEach((turn) => {
    if (turn.turnId) byId.set(turn.turnId, turn)
  })
  return [...byId.values()].sort(
    (left, right) => (left.turnSequence ?? 0) - (right.turnSequence ?? 0),
  )
}

export function isCoachRunTerminal(run: CoachingRunResponse) {
  return ['SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'INTERRUPTED'].includes(run.status ?? '')
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
