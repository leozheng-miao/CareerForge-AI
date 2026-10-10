import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import HomeView from '@/views/HomeView.vue'

const EmptyView = defineComponent({ template: '<div />' })

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomeView },
      { path: '/login', component: EmptyView },
      { path: '/register', component: EmptyView },
    ],
  })

  await router.push('/')
  await router.isReady()

  return mount(HomeView, {
    global: {
      plugins: [router],
    },
  })
}

describe('HomeView', () => {
  it('presents the three frozen product lines and working entry links', async () => {
    const wrapper = await mountHome()

    expect(wrapper.get('h1').text()).toContain('练成能拿 Offer 的能力')
    expect(wrapper.findAll('.capability-card')).toHaveLength(3)
    expect(wrapper.text()).toContain('岗位能力诊断')
    expect(wrapper.text()).toContain('个性化成长训练')
    expect(wrapper.text()).toContain('多 Agent 模拟面试')
    expect(wrapper.get('a[href="/register"]').text()).toContain('从一份 JD 开始职业诊断')
    expect(wrapper.get('a[href="/#interview"]').text()).toContain('体验 AI 模拟面试')
  })

  it('switches the interview preview without inventing a backend session', async () => {
    const wrapper = await mountHome()
    const tabs = wrapper.findAll('[role="tab"]')

    expect(tabs).toHaveLength(2)
    expect(tabs[0]?.attributes('aria-selected')).toBe('true')

    await tabs[1]?.trigger('click')

    expect(tabs[1]?.attributes('aria-selected')).toBe('true')
    expect(wrapper.text()).toContain('幂等键、超时与人工接管')
    expect(wrapper.text()).toContain('产品预览 · 示例')
  })
})
