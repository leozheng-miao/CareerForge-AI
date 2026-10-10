import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import LoginView from '@/views/auth/LoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'

async function mountAuthView(component: typeof LoginView | typeof RegisterView, path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/register', component: RegisterView },
      { path: '/app', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()

  return mount(component, {
    global: {
      plugins: [createPinia(), router],
    },
  })
}

describe('authentication views', () => {
  it('keeps invalid login input on the page and explains the validation error', async () => {
    const wrapper = await mountAuthView(LoginView, '/login')

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('请输入有效的邮箱地址')
    expect((wrapper.get('input[autocomplete="email"]').element as HTMLInputElement).value).toBe('')
  })

  it('rejects mismatched registration passwords before sending a request', async () => {
    const wrapper = await mountAuthView(RegisterView, '/register')
    const inputs = wrapper.findAll('input')

    await inputs[0]?.setValue('user@example.com')
    await inputs[1]?.setValue('用户')
    await inputs[2]?.setValue('password-1')
    await inputs[3]?.setValue('password-2')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('两次输入的密码不一致')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('user@example.com')
  })
})
