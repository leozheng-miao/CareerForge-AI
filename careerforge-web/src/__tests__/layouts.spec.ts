import { createPinia } from 'pinia'
import { defineComponent, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/features/auth/store/auth-store'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import AppLayout from '@/layouts/AppLayout.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { ROUTE_PATHS } from '@/router/paths'

function createTestRouter(initialPath: string = ROUTE_PATHS.home) {
  const EmptyView = defineComponent({ template: '<div />' })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: ROUTE_PATHS.home, component: EmptyView },
      { path: ROUTE_PATHS.login, name: 'login', component: EmptyView },
      { path: ROUTE_PATHS.register, component: EmptyView },
      { path: ROUTE_PATHS.terms, component: EmptyView },
      { path: ROUTE_PATHS.privacy, component: EmptyView },
      {
        path: ROUTE_PATHS.app,
        component: EmptyView,
        children: [
          {
            path: 'interviews',
            name: 'interviews',
            component: EmptyView,
            meta: { title: '开始面试' },
          },
          {
            path: 'interview-records',
            name: 'interview-records',
            component: EmptyView,
            meta: { title: '面试记录' },
          },
          {
            path: 'interviews/:interviewId/ready',
            name: 'interview-ready',
            component: EmptyView,
            meta: { title: '面试就绪', immersive: true },
          },
          {
            path: 'interviews/:interviewId/session',
            name: 'interview-session',
            component: EmptyView,
            meta: { title: '快速面试', immersive: true },
          },
        ],
      },
    ],
  })

  return { router, initialPath }
}

async function mountWithRouter(
  component: typeof PublicLayout | typeof AuthLayout | typeof AppLayout,
  initialPath: string,
) {
  const { router } = createTestRouter(initialPath)
  const pinia = createPinia()
  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router],
    },
    slots: {
      default: '<div data-testid="page-content">页面内容</div>',
    },
  })

  return { pinia, router, wrapper }
}

describe('application layouts', () => {
  afterEach(() => vi.useRealTimers())

  it('renders the public navigation and page content', async () => {
    const { wrapper } = await mountWithRouter(PublicLayout, ROUTE_PATHS.home)

    expect(wrapper.get('a[aria-label="水水面首页"]').text()).toContain('水水面')
    expect(wrapper.text()).toContain('面试模式')
    expect(wrapper.text()).toContain('怎么进行')
    expect(wrapper.text()).toContain('面试记录')
    expect(wrapper.text()).toContain('开始面试')
    expect(wrapper.text()).not.toContain('成长路径')
    expect(wrapper.text()).not.toContain('CareerForge-AI')
    expect(wrapper.find('[data-testid="page-content"]').exists()).toBe(true)
  })

  it('keeps authentication pages separate from the marketing navigation', async () => {
    const { wrapper } = await mountWithRouter(AuthLayout, ROUTE_PATHS.login)

    expect(wrapper.find('a[aria-label="水水面首页"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('返回首页')
    expect(wrapper.text()).not.toContain('面试模式')
    expect(wrapper.find('.public-footer').exists()).toBe(false)
    expect(wrapper.find('[data-testid="page-content"]').exists()).toBe(true)
  })

  it('only exposes interview entry points and marks the current section as active', async () => {
    const { wrapper } = await mountWithRouter(AppLayout, ROUTE_PATHS.interviews)
    const desktopNavigation = wrapper.get('.app-sidebar')
    const entries = desktopNavigation.findAll('.app-navigation__item')

    expect(entries).toHaveLength(2)
    expect(desktopNavigation.text()).toContain('开始面试')
    expect(desktopNavigation.text()).toContain('面试记录')
    expect(desktopNavigation.text()).not.toContain('成长总览')
    expect(desktopNavigation.text()).not.toContain('Career Coach')
    expect(desktopNavigation.get('.app-navigation__item--active').text()).toContain('开始面试')
    expect(wrapper.find('[data-testid="page-content"]').exists()).toBe(true)
  })

  it('logs out from the application header and returns to login', async () => {
    const { pinia, router, wrapper } = await mountWithRouter(AppLayout, ROUTE_PATHS.interviews)
    const authStore = useAuthStore(pinia)
    const interviewStore = useInterviewFlowStore(pinia)
    const pauseActiveBeforeLogout = vi
      .spyOn(interviewStore, 'pauseActiveBeforeLogout')
      .mockResolvedValue()
    const logout = vi.spyOn(authStore, 'logout').mockResolvedValue()

    expect(wrapper.find('[aria-label="打开账户菜单"]').exists()).toBe(true)
    await wrapper.get('[aria-label="打开账户菜单"]').trigger('click')
    await wrapper.get('button[aria-label="退出登录"]').trigger('click')
    await flushPromises()

    expect(pauseActiveBeforeLogout).toHaveBeenCalledTimes(1)
    expect(logout).toHaveBeenCalledTimes(1)
    expect(pauseActiveBeforeLogout.mock.invocationCallOrder[0]).toBeLessThan(
      logout.mock.invocationCallOrder[0] ?? Number.MAX_SAFE_INTEGER,
    )
    expect(router.currentRoute.value.path).toBe(ROUTE_PATHS.login)
  })

  it('keeps the active interview heartbeat running after an application route change', async () => {
    vi.useFakeTimers()
    const { pinia, router, wrapper } = await mountWithRouter(
      AppLayout,
      '/app/interviews/interview-1/session',
    )
    const interviewStore = useInterviewFlowStore(pinia)
    const heartbeat = vi.spyOn(interviewStore, 'heartbeat').mockResolvedValue(null)
    interviewStore.session = {
      interviewId: 'interview-1',
      mode: 'QUICK',
      stage: 'INTERVIEWING',
      version: 8,
    }
    interviewStore.timer = {
      interviewId: 'interview-1',
      timerState: 'RUNNING',
      remainingSeconds: 2_400,
      version: 8,
      leaseActive: true,
    }
    await nextTick()

    await router.push(ROUTE_PATHS.interviews)
    await vi.advanceTimersByTimeAsync(10_000)

    expect(heartbeat).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    vi.useRealTimers()
  })

  it('requests a best-effort pause when the application page closes', async () => {
    const { pinia, wrapper } = await mountWithRouter(
      AppLayout,
      '/app/interviews/interview-1/session',
    )
    const interviewStore = useInterviewFlowStore(pinia)
    const pauseForPageExit = vi.spyOn(interviewStore, 'pauseForPageExit')

    window.dispatchEvent(new Event('pagehide'))

    expect(pauseForPageExit).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('removes the application shell from immersive interview routes', async () => {
    const { wrapper } = await mountWithRouter(
      AppLayout,
      '/app/interviews/interview-1/session',
    )

    expect(wrapper.find('.app-sidebar').exists()).toBe(false)
    expect(wrapper.find('.app-header').exists()).toBe(false)
    expect(wrapper.get('.app-content').classes()).toContain('app-content--immersive')
    expect(wrapper.find('[data-testid="page-content"]').exists()).toBe(true)
  })

  it('also removes the application shell from the preparation and ready route', async () => {
    const { wrapper } = await mountWithRouter(
      AppLayout,
      '/app/interviews/interview-1/ready',
    )

    expect(wrapper.find('.app-sidebar').exists()).toBe(false)
    expect(wrapper.find('.app-header').exists()).toBe(false)
    expect(wrapper.get('.app-content').classes()).toContain('app-content--immersive')
  })
})
