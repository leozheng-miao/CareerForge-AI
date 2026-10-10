import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/features/auth/store/auth-store'
import { installAuthGuard } from '@/router/auth-guard'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/login',
        name: 'login',
        component: { template: '<div />' },
        meta: { guestOnly: true },
      },
      {
        path: '/app',
        name: 'app',
        component: { template: '<div />' },
        meta: { requiresAuth: true },
      },
    ],
  })
}

describe('authentication guard', () => {
  /**
   * @program: CareerForge-AI
   * @description: 验证已有Refresh Cookie的用户打开登录页时自动恢复并进入工作台
   * @author: Miao Zheng
   * @date: 2026-09-08
   **/
  it('restores an existing session before entering the login page', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createTestRouter()
    const store = useAuthStore(pinia)
    vi.spyOn(store, 'restoreSession').mockImplementation(async () => {
      store.acceptSession({
        userId: 'user-1',
        email: 'user@example.com',
        displayName: '用户',
        tokenType: 'Bearer',
        accessToken: 'restored-token',
        accessTokenExpiresAt: '2026-09-08T01:15:00Z',
      })
      return true
    })
    installAuthGuard(router, pinia)

    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/app')
    expect(store.restoreSession).toHaveBeenCalledTimes(1)
  })

  /**
   * @program: CareerForge-AI
   * @description: 验证没有有效Refresh Cookie的用户仍被引导至登录页
   * @author: Miao Zheng
   * @date: 2026-09-08
   **/
  it('redirects an anonymous user from protected routes to login', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createTestRouter()
    const store = useAuthStore(pinia)
    vi.spyOn(store, 'restoreSession').mockImplementation(async () => {
      store.clearSession()
      return false
    })
    installAuthGuard(router, pinia)

    await router.push('/app')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/app')
  })
})
