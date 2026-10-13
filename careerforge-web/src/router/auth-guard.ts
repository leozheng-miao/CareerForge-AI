import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'

import { useAuthStore } from '@/features/auth/store/auth-store'

import { ROUTE_PATHS } from './paths'

export function installAuthGuard(router: Router, pinia: Pinia) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    if ((to.meta.requiresAuth || to.meta.guestOnly) && authStore.status === 'unknown') {
      await authStore.restoreSession()
    }
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return { path: ROUTE_PATHS.login, query: { redirect: to.fullPath } }
    }
    if (to.meta.guestOnly && authStore.isAuthenticated) return ROUTE_PATHS.interviews
    return true
  })
}
