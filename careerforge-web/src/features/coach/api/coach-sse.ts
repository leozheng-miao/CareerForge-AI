import type { CoachingRunResponseStatus } from '@/shared/api/generated/model'
import { clearSseResumeEventId, connectAuthenticatedSse } from '@/shared/sse/sse-client'

export interface CoachingRunEvent {
  runId: string
  type: string
  status: CoachingRunResponseStatus
  toolName?: string
  toolStatus?: string
  source: 'REDIS_STREAM' | 'MYSQL_TERMINAL_SNAPSHOT'
  occurredAt: string
}

const TERMINAL_EVENTS = new Set(['RUN_COMPLETED', 'RUN_FAILED'])

export function observeCoachRun(
  actorId: string,
  runId: string,
  signal: AbortSignal,
  handlers: {
    onEvent: (event: CoachingRunEvent) => void
    onTerminal: () => void | Promise<void>
    onReconnect: (attempt: number) => void
  },
) {
  const resumeKey = `coach:${actorId}:${runId}`
  return connectAuthenticatedSse<CoachingRunEvent>({
    path: `/api/coaching-runs/${encodeURIComponent(runId)}/events`,
    resumeKey,
    signal,
    terminalEvents: TERMINAL_EVENTS,
    onEvent: ({ data }) => handlers.onEvent(data),
    onTerminal: handlers.onTerminal,
    onReconnect: handlers.onReconnect,
  })
}

export function clearCoachRunResume(actorId: string, runId: string) {
  clearSseResumeEventId(`coach:${actorId}:${runId}`)
}
