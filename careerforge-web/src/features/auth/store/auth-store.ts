import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  login as requestLogin,
  logout as requestLogout,
  refreshSession as requestRefreshSession,
  register as requestRegister,
} from '@/shared/api/generated/careerforge'
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/errors'

export interface AuthenticatedUser {
  userId: string
  email: string
  displayName: string
}

interface AuthSessionPayload extends AuthenticatedUser {
  tokenType: string
  accessToken: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt?: string
}

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const currentUser = ref<AuthenticatedUser | null>(null)
  const status = ref<AuthStatus>('unknown')
  let restorePromise: Promise<boolean> | null = null

  const isAuthenticated = computed(
    () => status.value === 'authenticated' && Boolean(accessToken.value),
  )

  function acceptSession(payload: AuthSessionPayload) {
    accessToken.value = payload.accessToken
    currentUser.value = {
      userId: payload.userId,
      email: payload.email,
      displayName: payload.displayName,
    }
    status.value = 'authenticated'
  }

  function clearSession() {
    accessToken.value = null
    currentUser.value = null
    status.value = 'anonymous'
  }

  async function login(credentials: LoginRequest) {
    const response = await requestLogin(credentials)
    const session = requireAuthSession(response.data)
    acceptSession(session)
    return session
  }

  async function register(account: RegisterRequest) {
    const response = await requestRegister(account)
    const session = requireAuthSession(response.data)
    acceptSession(session)
    return session
  }

  async function refreshAccessToken() {
    return withRefreshLock(async () => {
      const response = await requestRefreshSession()
      const session = requireAuthSession(response.data)
      acceptSession(session)
      return session.accessToken
    })
  }

  async function restoreSession() {
    if (isAuthenticated.value) return true
    if (status.value === 'anonymous') return false
    restorePromise ??= refreshAccessToken()
      .then(() => true)
      .catch(() => {
        clearSession()
        return false
      })
      .finally(() => {
        restorePromise = null
      })
    return restorePromise
  }

  async function logout() {
    try {
      await requestLogout()
    } finally {
      clearSession()
    }
  }

  return {
    accessToken,
    currentUser,
    status,
    isAuthenticated,
    acceptSession,
    clearSession,
    login,
    register,
    logout,
    refreshAccessToken,
    restoreSession,
  }
})

async function withRefreshLock<T>(operation: () => Promise<T>): Promise<T> {
  if (typeof navigator === 'undefined' || !('locks' in navigator)) return operation()
  return navigator.locks.request('careerforge-refresh-token', operation)
}

function requireAuthSession(payload?: AuthResponse): AuthSessionPayload {
  if (
    !payload ||
    typeof payload.userId !== 'string' ||
    typeof payload.email !== 'string' ||
    typeof payload.displayName !== 'string' ||
    typeof payload.tokenType !== 'string' ||
    typeof payload.accessToken !== 'string' ||
    typeof payload.accessTokenExpiresAt !== 'string'
  ) {
    throw new ApiError({ kind: 'unknown', message: '认证响应不完整，请稍后重试' })
  }
  return {
    userId: payload.userId,
    email: payload.email,
    displayName: payload.displayName,
    tokenType: payload.tokenType,
    accessToken: payload.accessToken,
    accessTokenExpiresAt: payload.accessTokenExpiresAt,
    refreshTokenExpiresAt: payload.refreshTokenExpiresAt,
  }
}
