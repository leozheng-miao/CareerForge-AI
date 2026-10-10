import axios, { type AxiosError } from 'axios'

export type ApiErrorKind =
  | 'validation'
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'rate-limit'
  | 'unavailable'
  | 'network'
  | 'cancelled'
  | 'unknown'

interface ApiErrorOptions {
  kind: ApiErrorKind
  message: string
  code?: number
  httpStatus?: number
  cause?: unknown
}

interface ErrorEnvelope {
  code?: unknown
  message?: unknown
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly code?: number
  readonly httpStatus?: number
  readonly cause?: unknown

  constructor({ kind, message, code, httpStatus, cause }: ApiErrorOptions) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.code = code
    this.httpStatus = httpStatus
    this.cause = cause
  }

  get isVersionConflict() {
    return this.kind === 'conflict'
  }
}

export function classifyApiError(httpStatus?: number, code?: number): ApiErrorKind {
  if (code === 40101 || httpStatus === 403 || code === 40300 || code === 40310) {
    return 'forbidden'
  }
  if (
    httpStatus === 401 ||
    code === 40100 ||
    (code !== undefined && code >= 40110 && code < 40200)
  ) {
    return 'unauthorized'
  }
  if (
    httpStatus === 400 ||
    code === 40000 ||
    (code !== undefined && code >= 40010 && code < 40100)
  ) {
    return 'validation'
  }
  if (httpStatus === 404 || code === 40400) {
    return 'not-found'
  }
  if (httpStatus === 409 || code === 40900 || code === 40910) {
    return 'conflict'
  }
  if (httpStatus === 429 || code === 42900) {
    return 'rate-limit'
  }
  if (
    (httpStatus !== undefined && httpStatus >= 500) ||
    code === 50000 ||
    code === 50001 ||
    code === 50300
  ) {
    return 'unavailable'
  }
  return 'unknown'
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (axios.isCancel(error)) {
    return new ApiError({ kind: 'cancelled', message: '请求已取消', cause: error })
  }

  if (!axios.isAxiosError(error)) {
    return new ApiError({ kind: 'unknown', message: '发生未知错误', cause: error })
  }

  const axiosError = error as AxiosError<ErrorEnvelope>
  if (!axiosError.response) {
    return new ApiError({
      kind: 'network',
      message: '网络连接失败，请检查网络后重试',
      cause: error,
    })
  }

  const status = axiosError.response.status
  const responseCode = axiosError.response.data?.code
  const code = typeof responseCode === 'number' ? responseCode : undefined
  const responseMessage = axiosError.response.data?.message

  return new ApiError({
    kind: classifyApiError(status, code),
    code,
    httpStatus: status,
    message:
      typeof responseMessage === 'string' && responseMessage
        ? responseMessage
        : fallbackMessage(status),
    cause: error,
  })
}

function fallbackMessage(status: number) {
  if (status === 401) return '登录状态已失效，请重新登录'
  if (status === 403) return '当前账户无权执行此操作'
  if (status === 404) return '请求的数据不存在'
  if (status === 409) return '数据状态已发生变化，请刷新后重试'
  if (status === 429) return '请求过于频繁，请稍后重试'
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  return '请求处理失败'
}
