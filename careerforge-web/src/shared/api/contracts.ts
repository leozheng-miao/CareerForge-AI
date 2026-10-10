import { ApiError, classifyApiError } from './errors'

export interface BaseResponse<T> {
  code: number
  data: T | null
  message: string
}

export function isBaseResponse(value: unknown): value is BaseResponse<unknown> {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.code === 'number' &&
    typeof candidate.message === 'string' &&
    'data' in candidate
  )
}

export function unwrapBaseResponse<T>(response: BaseResponse<T>): T {
  if (response.code !== 0) {
    throw new ApiError({
      kind: classifyApiError(undefined, response.code),
      code: response.code,
      message: response.message || '请求处理失败',
    })
  }

  return response.data as T
}
