import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppLayout from '@/layouts/AppLayout.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { ROUTE_PATHS } from '@/router/paths'

function createTestRouter(initialPath: string = ROUTE_PATHS.home) {
  const EmptyView = defineComponent({ template: '<div />' })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: ROUTE_PATHS.home, component: EmptyView },
      { path: ROUTE_PATHS.login, component: EmptyView },
      { path: ROUTE_PATHS.register, component: EmptyView },
      { path: ROUTE_PATHS.app, name: 'dashboard', component: EmptyView },
      {
        path: ROUTE_PATHS.targetRole,
        name: 'target-role',
        component: EmptyView,
        meta: { title: '目标岗位' },
      },
      {
        path: `${ROUTE_PATHS.app}/:pathMatch(.*)*`,
        component: EmptyView,
        meta: { title: '成长工作台' },
      },
    ],
  })

  return { router, initialPath }
}

async function mountWithRouter(
  component: typeof PublicLayout | typeof AppLayout,
  initialPath: string,
) {
  const { router } = createTestRouter(initialPath)
  await router.push(initialPath)
  await router.isReady()

  return mount(component, {
    global: {
      plugins: [router],
    },
    slots: {
      default: '<div data-testid="page-content">页面内容</div>',
    },
  })
}

describe('application layouts', () => {
  it('renders the public navigation and page content', async () => {
    const wrapper = await mountWithRouter(PublicLayout, ROUTE_PATHS.home)

    expect(wrapper.text()).toContain('产品能力')
    expect(wrapper.text()).toContain('免费开始')
    expect(wrapper.find('[data-testid="page-content"]').exists()).toBe(true)
  })

  it('marks the current workspace section as active', async () => {
    const wrapper = await mountWithRouter(AppLayout, ROUTE_PATHS.targetRole)
    const activeNavigation = wrapper.find('.app-navigation__item--active')

    expect(activeNavigation.text()).toContain('目标岗位')
    expect(wrapper.find('[data-testid="page-content"]').exists()).toBe(true)
  })

  it('keeps routes from later checkpoints visibly disabled', async () => {
    const wrapper = await mountWithRouter(AppLayout, ROUTE_PATHS.app)
    const disabledItems = wrapper.findAll('[aria-disabled="true"]')

    expect(disabledItems.some((item) => item.text().includes('开始面试'))).toBe(true)
    expect(disabledItems.some((item) => item.text().includes('面试记录'))).toBe(true)
    expect(wrapper.findAll('.app-navigation__item--active')).toHaveLength(1)
  })
})
