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
      { path: '/terms', component: EmptyView },
      { path: '/privacy', component: EmptyView },
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
  it('presents the selected editorial direction and two interview modes', async () => {
    const wrapper = await mountHome()

    expect(wrapper.get('h1').text()).toContain('面试前，先认真面一次。')
    expect(wrapper.text()).toContain('会根据回答继续追问的技术面试。')
    expect(wrapper.text()).toContain('Java 后端 · AI Agent 开发')
    expect(wrapper.findAll('.home-mode-entry')).toHaveLength(2)
    expect(wrapper.text()).toContain('快速面试')
    expect(wrapper.text()).toContain('综合面试')
    expect(wrapper.text()).toContain('60 分钟 · 8 个考查目标')
    expect(wrapper.text()).toContain('结合岗位与真实经历')
    expect(wrapper.get('a[href="/register"]').text()).toContain('开始面试')
    expect(wrapper.find('.interview-preview').exists()).toBe(true)
  })

  it('does not advertise retired growth-product capabilities', async () => {
    const wrapper = await mountHome()

    expect(wrapper.text()).not.toContain('岗位能力诊断')
    expect(wrapper.text()).not.toContain('个性化成长训练')
    expect(wrapper.text()).not.toContain('Career Coach')
    expect(wrapper.text()).not.toContain('长期记忆')
    expect(wrapper.text()).not.toContain('AI 赋能')
    expect(wrapper.text()).not.toContain('精准洞察')
  })
})
