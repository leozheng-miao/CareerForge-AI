import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/features/auth/store/auth-store'
import LoginView from '@/views/auth/LoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'

async function mountAuthView(component: typeof LoginView | typeof RegisterView, path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/register', component: RegisterView },
      { path: '/terms', component: { template: '<div />' } },
      { path: '/privacy', component: { template: '<div />' } },
      { path: '/app', component: { template: '<div />' } },
      { path: '/app/interviews', component: { template: '<div />' } },
      { path: '/app/interview-records', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()

  const pinia = createPinia()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router],
    },
  })

  return { pinia, router, wrapper }
}

describe('authentication views', () => {
  it('keeps authentication focused and uses user-facing security copy', async () => {
    const { wrapper } = await mountAuthView(LoginView, '/login')

    expect(wrapper.text()).toContain('登录水水面')
    expect(wrapper.text()).toContain('继续未完成的面试')
    expect(wrapper.find('a[href="/terms"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/privacy"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Access Token')
    expect(wrapper.text()).not.toContain('能力差距')
    expect(wrapper.text()).not.toContain('训练计划')
  })

  it('keeps invalid login input on the page and explains the validation error', async () => {
    const { wrapper } = await mountAuthView(LoginView, '/login')

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('请输入有效的邮箱地址')
    expect((wrapper.get('input[autocomplete="email"]').element as HTMLInputElement).value).toBe('')
  })

  it('rejects mismatched registration passwords before sending a request', async () => {
    const { wrapper } = await mountAuthView(RegisterView, '/register')
    const inputs = wrapper.findAll('input')

    await inputs[0]?.setValue('user@example.com')
    await inputs[1]?.setValue('用户')
    await inputs[2]?.setValue('password-1')
    await inputs[3]?.setValue('password-2')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('两次输入的密码不一致')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('user@example.com')
  })

  it.each([
    ['/login?redirect=%2Fapp%2Finterview-records', '/app/interview-records'],
    ['/login?redirect=%2F%2Fevil.example', '/app/interviews'],
  ])('restores only a safe in-app redirect after login', async (loginPath, expectedPath) => {
    const { pinia, router, wrapper } = await mountAuthView(LoginView, loginPath)
    const authStore = useAuthStore(pinia)
    vi.spyOn(authStore, 'login').mockResolvedValue({
      userId: 'user-1',
      email: 'user@example.com',
      displayName: '用户',
      tokenType: 'Bearer',
      accessToken: 'access-token',
      accessTokenExpiresAt: '2026-10-12T12:00:00Z',
    })

    await wrapper.get('input[autocomplete="email"]').setValue('user@example.com')
    await wrapper.get('input[autocomplete="current-password"]').setValue('password-1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe(expectedPath)
  })
})
