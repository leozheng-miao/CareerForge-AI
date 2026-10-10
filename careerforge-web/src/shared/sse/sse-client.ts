import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source'
import type { EventSourceMessage } from '@microsoft/fetch-event-source'

import { clearAuthSession, getAccessToken, refreshSessionOnce } from '@/shared/api/session-bridge'

const MAX_RECONNECTS = 4
const RETRY_DELAYS_MS = [500, 1_000, 2_000, 4_000] as const
const RESUME_PREFIX = 'careerforge:sse:'
const IDLE_WATCHDOG_MS = 35_000

class FatalSseError extends Error {}
class RetryableSseError extends Error {}

export interface SafeSseEvent<T> {
  id: string | null
  name: string
  data: T
}

export interface SseConnectionOptions<T> {
  path: string
  resumeKey: string
  signal?: AbortSignal
  terminalEvents: ReadonlySet<string>
  isTerminal?: (event: SafeSseEvent<T>) => boolean
  onEvent: (event: SafeSseEvent<T>) => void | Promise<void>
  onTerminal: (event: SafeSseEvent<T>) => void | Promise<void>
  onReconnect?: (attempt: number) => void
}

export async function connectAuthenticatedSse<T>({
  path,
  resumeKey,
  signal,
  terminalEvents,
  isTerminal,
  onEvent,
  onTerminal,
  onReconnect,
}: SseConnectionOptions<T>) {
  let reconnects = 0
  let authRefreshAttempted = false
  let terminalReceived = false
  let lastDeliveredId = readResumeEventId(resumeKey)
  let activeRequestController: AbortController | null = null
  let watchdogTimer: number | null = null
  let handlerFailure: unknown
  let pendingHandlers = Promise.resolve()

  const headers: Record<string, string> = { Accept: EventStreamContentType }
  if (lastDeliveredId) headers['Last-Event-ID'] = lastDeliveredId

  function resetWatchdog() {
    if (watchdogTimer !== null) window.clearTimeout(watchdogTimer)
    watchdogTimer = window.setTimeout(() => activeRequestController?.abort(), IDLE_WATCHDOG_MS)
  }

  try {
    await fetchEventSource(resolveApiUrl(path), {
      method: 'GET',
      headers,
      signal,
      openWhenHidden: true,
      credentials: 'include',
      fetch: async (input, init) => {
        activeRequestController?.abort()
        const requestController = new AbortController()
        activeRequestController = requestController
        init?.signal?.addEventListener('abort', () => requestController.abort(), { once: true })
        const requestHeaders = new Headers(init?.headers)
        const accessToken = getAccessToken()
        if (accessToken) requestHeaders.set('Authorization', `Bearer ${accessToken}`)
        resetWatchdog()
        return fetch(input, {
          ...init,
          headers: requestHeaders,
          credentials: 'include',
          signal: requestController.signal,
        })
      },
      async onopen(response) {
        resetWatchdog()
        if (response.status === 401 && !authRefreshAttempted) {
          authRefreshAttempted = true
          const token = await refreshSessionOnce()
          if (token) throw new RetryableSseError('登录凭证已刷新，重新连接事件流')
          clearAuthSession()
          throw new FatalSseError('登录状态已失效，请重新登录')
        }
        if (response.status === 401 || response.status === 403) {
          clearAuthSession()
          throw new FatalSseError('登录状态已失效或无权访问该事件流')
        }
        if (response.status === 404) throw new FatalSseError('事件流不存在或不属于当前账户')
        if (!response.ok) throw new RetryableSseError(`事件流连接失败（HTTP ${response.status}）`)
        const contentType = response.headers.get('content-type')
        if (!contentType?.startsWith(EventStreamContentType)) {
          throw new FatalSseError('服务端返回的不是事件流')
        }
        authRefreshAttempted = false
      },
      onmessage(message: EventSourceMessage) {
        resetWatchdog()
        if (message.id && message.id === lastDeliveredId) return
        const event = parseEvent<T>(message)
        if (message.id) {
          lastDeliveredId = message.id
          writeResumeEventId(resumeKey, message.id)
        }
        const isTerminalEvent =
          !terminalReceived && (terminalEvents.has(message.event) || isTerminal?.(event))
        if (isTerminalEvent) terminalReceived = true
        pendingHandlers = pendingHandlers
          .then(async () => {
            await onEvent(event)
            if (isTerminalEvent) await onTerminal(event)
          })
          .catch((error: unknown) => {
            handlerFailure = error
            activeRequestController?.abort()
          })
      },
      onclose() {
        if (!terminalReceived && !signal?.aborted) {
          throw new RetryableSseError('事件流在业务终态前关闭')
        }
      },
      onerror(error) {
        if (handlerFailure) throw handlerFailure
        if (error instanceof FatalSseError || signal?.aborted) throw error
        reconnects += 1
        if (reconnects > MAX_RECONNECTS) {
          throw new FatalSseError('事件流重连次数已达上限，请通过页面刷新回读最终状态')
        }
        onReconnect?.(reconnects)
        return RETRY_DELAYS_MS[Math.min(reconnects - 1, RETRY_DELAYS_MS.length - 1)]
      },
    })
    await pendingHandlers
    if (handlerFailure) throw handlerFailure
  } finally {
    if (watchdogTimer !== null) window.clearTimeout(watchdogTimer)
    const requestController = activeRequestController as AbortController | null
    requestController?.abort()
  }
}

export function clearSseResumeEventId(resumeKey: string) {
  if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(storageKey(resumeKey))
}

export function readResumeEventId(resumeKey: string) {
  if (typeof sessionStorage === 'undefined') return null
  return sessionStorage.getItem(storageKey(resumeKey))
}

function writeResumeEventId(resumeKey: string, eventId: string) {
  if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(storageKey(resumeKey), eventId)
}

function storageKey(resumeKey: string) {
  return `${RESUME_PREFIX}${resumeKey}`
}

function parseEvent<T>(message: EventSourceMessage): SafeSseEvent<T> {
  try {
    return { id: message.id || null, name: message.event, data: JSON.parse(message.data) as T }
  } catch {
    throw new FatalSseError('事件流数据格式不合法')
  }
}

function resolveApiUrl(path: string) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/$/, '') ?? ''
  return `${baseUrl}${path}`
}
