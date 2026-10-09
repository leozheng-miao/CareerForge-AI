import { connectAuthenticatedSse } from '@/shared/sse/sse-client'

export interface InterviewFlowEvent {
  eventId?: string; interviewId: string; type?: string; resourceId?: string
  status: string; version: number; occurredAt?: string; eventTime?: string
}

export function observeInterviewFlow(actorId: string, interviewId: string,
  signal: AbortSignal, onEvent: (event: InterviewFlowEvent, name: string) => void | Promise<void>) {
  return connectAuthenticatedSse<InterviewFlowEvent>({
    path: `/api/interviews/${encodeURIComponent(interviewId)}/events`,
    resumeKey: `interview-flow:${actorId}:${interviewId}`,
    signal,
    terminalEvents: new Set(['report_ready']),
    isTerminal: ({ name }) => name === 'report_ready',
    onEvent: ({ data, name }) => onEvent(data, name),
    onTerminal: () => undefined,
    onReconnect: () => undefined,
  })
}
