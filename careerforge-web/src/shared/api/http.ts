import axios, {
  AxiosHeaders,
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'

import { isBaseResponse } from './contracts'
import { ApiError, classifyApiError, toApiError } from './errors'
import { clearAuthSession, getAccessToken, refreshSessionOnce } from './session-bridge'

const API_TIMEOUT_MS = 15_000
const PUBLIC_AUTH_PATHS = new Set([
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/logout',
])

type RetriableRequestConfig = InternalAxiosRequestConfig & {
  careerForgeRetriedAfterRefresh?: boolean
}

export function createHttpClient(): AxiosInstance {
  const client = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL?.trim() || '',
    timeout: API_TIMEOUT_MS,
    withCredentials: true,
    headers: {
      Accept: 'application/json',
    },
  })

  client.interceptors.request.use((config) => {
    if (isPublicAuthRequest(config.url)) {
      return config
    }

    const accessToken = getAccessToken()
    if (accessToken) {
      const headers = AxiosHeaders.from(config.headers)
      if (!headers.has('Authorization')) {
        headers.set('Authorization', `Bearer ${accessToken}`)
      }
      config.headers = headers
    }

    return config
  })

  client.interceptors.response.use(
    (response) => {
      if (isBaseResponse(response.data) && response.data.code !== 0) {
        throw new ApiError({
          kind: classifyApiError(response.status, response.data.code),
          code: response.data.code,
          httpStatus: response.status,
          message: response.data.message || '请求处理失败',
        })
      }
      return response
    },
    async (error: unknown) => {
      if (!axios.isAxiosError(error)) {
        throw toApiError(error)
      }

      const axiosError = error as AxiosError
      const originalRequest = axiosError.config as RetriableRequestConfig | undefined
      if (!shouldRefresh(axiosError, originalRequest)) {
        throw toApiError(error)
      }

      originalRequest.careerForgeRetriedAfterRefresh = true
      try {
        const accessToken = await refreshSessionOnce()
        if (!accessToken) {
          clearAuthSession()
          throw toApiError(error)
        }

        const headers = AxiosHeaders.from(originalRequest.headers)
        headers.set('Authorization', `Bearer ${accessToken}`)
        originalRequest.headers = headers
        const response = await client.request(originalRequest)
        return response
      } catch (refreshError) {
        clearAuthSession()
        throw toApiError(refreshError)
      }
    },
  )

  return client
}

function shouldRefresh(
  error: AxiosError,
  config?: RetriableRequestConfig,
): config is RetriableRequestConfig {
  return Boolean(
    error.response?.status === 401 &&
    config &&
    !isPublicAuthRequest(config.url) &&
    !config.careerForgeRetriedAfterRefresh,
  )
}

function isPublicAuthRequest(url?: string) {
  return url ? PUBLIC_AUTH_PATHS.has(url.split('?')[0] ?? '') : false
}

export const httpClient = createHttpClient()
