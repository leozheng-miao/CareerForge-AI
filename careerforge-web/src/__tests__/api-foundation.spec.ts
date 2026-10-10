import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/features/auth/store/auth-store'
import { isBaseResponse, unwrapBaseResponse } from '@/shared/api/contracts'
import { ApiError, classifyApiError } from '@/shared/api/errors'
import { createHttpClient, httpClient } from '@/shared/api/http'
import { refreshSessionOnce, registerAuthSessionAdapter } from '@/shared/api/session-bridge'

function axiosResponse(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown,
): AxiosResponse {
  return {
    config,
    data,
    headers: new AxiosHeaders(),
    status,
    statusText: String(status),
  }
}

function unauthorized(config: InternalAxiosRequestConfig) {
  const response = axiosResponse(config, 401, {
    code: 40110,
    data: null,
    message: 'Access Token 已失效',
  })
  return new AxiosError('Unauthorized', AxiosError.ERR_BAD_REQUEST, config, undefined, response)
}

describe('API foundation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('recognizes and unwraps a successful base response', () => {
    const response = {
      code: 0,
      data: { value: 'ok' },
      message: 'ok',
    }

    expect(isBaseResponse(response)).toBe(true)
    expect(unwrapBaseResponse(response)).toEqual({ value: 'ok' })
  })

  it('maps an HTTP 200 business conflict to a typed error', () => {
    expect(() => unwrapBaseResponse({ code: 40900, data: null, message: '版本冲突' })).toThrow(
      ApiError,
    )
    expect(classifyApiError(200, 40900)).toBe('conflict')
  })

  it('shares one refresh promise across concurrent unauthorized requests', async () => {
    const refreshAccessToken = vi.fn<() => Promise<string>>().mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(() => resolve('new-access-token'), 5)
        }),
    )
    const unregister = registerAuthSessionAdapter({
      getAccessToken: () => null,
      refreshAccessToken,
      clearSession: vi.fn<() => void>(),
    })

    const [first, second] = await Promise.all([refreshSessionOnce(), refreshSessionOnce()])

    expect(first).toBe('new-access-token')
    expect(second).toBe('new-access-token')
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    unregister()
  })

  it('keeps the access token only in Pinia state', () => {
    const store = useAuthStore()
    store.acceptSession({
      userId: 'user-1',
      email: 'user@example.com',
      displayName: '用户',
      tokenType: 'Bearer',
      accessToken: 'memory-only-token',
      accessTokenExpiresAt: '2026-09-05T00:00:00Z',
    })

    expect(store.isAuthenticated).toBe(true)
    expect(store.accessToken).toBe('memory-only-token')
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('accessToken')).toBeNull()
  })

  it('accepts login while keeping all tokens out of browser storage', async () => {
    const originalAdapter = httpClient.defaults.adapter
    let requestUrl: string | undefined
    let requestMethod: string | undefined
    let requestData: unknown
    httpClient.defaults.adapter = async (config) => {
      requestUrl = config.url
      requestMethod = config.method
      requestData = config.data
      return axiosResponse(config, 200, {
        code: 0,
        message: 'ok',
        data: {
          userId: 'user-1',
          email: 'user@example.com',
          displayName: '用户',
          tokenType: 'Bearer',
          accessToken: 'memory-only-token',
          accessTokenExpiresAt: '2026-09-05T00:00:00Z',
          refreshTokenExpiresAt: '2026-10-05T00:00:00Z',
        },
      })
    }

    try {
      const store = useAuthStore()
      await store.login({ email: 'user@example.com', password: 'password' })

      expect(requestUrl).toBe('/api/auth/login')
      expect(requestMethod).toBe('post')
      expect(typeof requestData === 'string' ? JSON.parse(requestData) : requestData).toEqual({
        email: 'user@example.com',
        password: 'password',
      })
      expect(store.accessToken).toBe('memory-only-token')
      expect(localStorage.getItem('refreshToken')).toBeNull()
      expect(sessionStorage.getItem('refreshToken')).toBeNull()
    } finally {
      httpClient.defaults.adapter = originalAdapter
    }
  })

  it('restores one session through the cookie-backed refresh endpoint', async () => {
    const originalAdapter = httpClient.defaults.adapter
    let refreshCalls = 0
    let authorization: string | undefined
    let withCredentials: boolean | undefined
    httpClient.defaults.adapter = async (config) => {
      refreshCalls += 1
      authorization = AxiosHeaders.from(config.headers).get('Authorization')?.toString()
      withCredentials = config.withCredentials
      return axiosResponse(config, 200, {
        code: 0,
        message: 'ok',
        data: {
          userId: 'user-1',
          email: 'user@example.com',
          displayName: '用户',
          tokenType: 'Bearer',
          accessToken: 'restored-access-token',
          accessTokenExpiresAt: '2026-09-08T01:15:00Z',
          refreshTokenExpiresAt: '2026-10-08T01:00:00Z',
        },
      })
    }

    try {
      const store = useAuthStore()
      const [first, second] = await Promise.all([store.restoreSession(), store.restoreSession()])

      expect(first).toBe(true)
      expect(second).toBe(true)
      expect(refreshCalls).toBe(1)
      expect(authorization).toBeUndefined()
      expect(withCredentials).toBe(true)
      expect(store.isAuthenticated).toBe(true)
      expect(store.accessToken).toBe('restored-access-token')
    } finally {
      httpClient.defaults.adapter = originalAdapter
    }
  })

  it('logs out without sending a Bearer token and clears the Pinia session', async () => {
    const originalAdapter = httpClient.defaults.adapter
    let requestUrl: string | undefined
    let authorization: string | undefined
    httpClient.defaults.adapter = async (config) => {
      requestUrl = config.url
      authorization = AxiosHeaders.from(config.headers).get('Authorization')?.toString()
      return axiosResponse(config, 200, { code: 0, data: null, message: 'ok' })
    }

    try {
      const store = useAuthStore()
      store.acceptSession({
        userId: 'user-1',
        email: 'user@example.com',
        displayName: '用户',
        tokenType: 'Bearer',
        accessToken: 'access-token',
        accessTokenExpiresAt: '2026-09-08T01:15:00Z',
      })
      await store.logout()

      expect(requestUrl).toBe('/api/auth/logout')
      expect(authorization).toBeUndefined()
      expect(store.isAuthenticated).toBe(false)
      expect(store.accessToken).toBeNull()
      expect(store.currentUser).toBeNull()
    } finally {
      httpClient.defaults.adapter = originalAdapter
    }
  })

  it('injects the in-memory access token into protected requests', async () => {
    const unregister = registerAuthSessionAdapter({
      getAccessToken: () => 'access-token-for-test',
      refreshAccessToken: vi.fn<() => Promise<string | null>>(),
      clearSession: vi.fn<() => void>(),
    })
    const client = createHttpClient()
    let authorization: string | undefined
    client.defaults.adapter = async (config) => {
      authorization = AxiosHeaders.from(config.headers).get('Authorization')?.toString()
      return axiosResponse(config, 200, { code: 0, data: {}, message: 'ok' })
    }

    await client.get('/api/me')

    expect(authorization).toBe('Bearer access-token-for-test')
    unregister()
  })

  it('rejects a non-zero business code even when HTTP status is 200', async () => {
    const client = createHttpClient()
    client.defaults.adapter = async (config) =>
      axiosResponse(config, 200, { code: 40900, data: null, message: '请求冲突' })

    await expect(client.get('/api/resource')).rejects.toMatchObject({
      kind: 'conflict',
      code: 40900,
      httpStatus: 200,
    })
  })

  it('refreshes once and replays each concurrent request once', async () => {
    let accessToken = 'expired-token'
    const refreshAccessToken = vi.fn<() => Promise<string>>().mockImplementation(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5))
      accessToken = 'refreshed-token'
      return accessToken
    })
    const unregister = registerAuthSessionAdapter({
      getAccessToken: () => accessToken,
      refreshAccessToken,
      clearSession: vi.fn<() => void>(),
    })
    const client = createHttpClient()
    let adapterCalls = 0
    client.defaults.adapter = async (config) => {
      adapterCalls += 1
      const authorization = AxiosHeaders.from(config.headers).get('Authorization')
      if (authorization !== 'Bearer refreshed-token') {
        throw unauthorized(config)
      }
      return axiosResponse(config, 200, { code: 0, data: {}, message: 'ok' })
    }

    await Promise.all([client.get('/api/me'), client.get('/api/me')])

    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(adapterCalls).toBe(4)
    unregister()
  })

  it('does not enter a refresh loop when the replay is still unauthorized', async () => {
    const refreshAccessToken = vi
      .fn<() => Promise<string>>()
      .mockResolvedValue('still-invalid-token')
    const clearSession = vi.fn<() => void>()
    const unregister = registerAuthSessionAdapter({
      getAccessToken: () => 'expired-token',
      refreshAccessToken,
      clearSession,
    })
    const client = createHttpClient()
    let adapterCalls = 0
    client.defaults.adapter = async (config) => {
      adapterCalls += 1
      throw unauthorized(config)
    }

    await expect(client.get('/api/me')).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(adapterCalls).toBe(2)
    expect(clearSession).toHaveBeenCalled()
    unregister()
  })
})
