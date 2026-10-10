export interface AuthSessionAdapter {
  getAccessToken: () => string | null
  refreshAccessToken: () => Promise<string | null>
  clearSession: () => void
}

let sessionAdapter: AuthSessionAdapter | null = null
let refreshPromise: Promise<string | null> | null = null

export function registerAuthSessionAdapter(adapter: AuthSessionAdapter) {
  sessionAdapter = adapter

  return () => {
    if (sessionAdapter === adapter) {
      sessionAdapter = null
      refreshPromise = null
    }
  }
}

export function getAccessToken() {
  return sessionAdapter?.getAccessToken() ?? null
}

export async function refreshSessionOnce() {
  if (!sessionAdapter) {
    return null
  }

  refreshPromise ??= sessionAdapter.refreshAccessToken().finally(() => {
    refreshPromise = null
  })

  return refreshPromise
}

export function clearAuthSession() {
  sessionAdapter?.clearSession()
}
