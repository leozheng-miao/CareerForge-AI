<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Alert, Button, Input, InputPassword } from 'ant-design-vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import AuthPanel from '@/features/auth/components/AuthPanel.vue'
import { useAuthStore } from '@/features/auth/store/auth-store'
import { ROUTE_PATHS } from '@/router/paths'
import { toApiError } from '@/shared/api/errors'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const form = reactive({ email: '', password: '' })
const submitting = ref(false)
const errorMessage = ref('')
const validationMessage = ref('')

async function submit() {
  validationMessage.value = validate()
  if (validationMessage.value) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await authStore.login({ email: form.email.trim(), password: form.password })
    await router.replace(resolveRedirect(route.query.redirect))
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    submitting.value = false
  }
}

function validate() {
  if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) {
    return '请输入有效的邮箱地址'
  }
  if (!form.password) {
    return '请输入密码'
  }
  return ''
}

function resolveRedirect(value: unknown) {
  if (typeof value === 'string' && value.startsWith('/app') && !value.startsWith('//')) {
    return value
  }
  return ROUTE_PATHS.interviews
}
</script>

<template>
  <AuthPanel
    id="login"
    eyebrow="欢迎回来"
    title="登录水水面"
    description="继续未完成的面试，或查看已经生成的正式报告。"
  >
    <Alert v-if="errorMessage" class="auth-alert" type="error" show-icon :message="errorMessage" />
    <Alert
      v-else-if="validationMessage"
      class="auth-alert"
      type="warning"
      show-icon
      :message="validationMessage"
    />

    <form class="auth-form" novalidate @submit.prevent="submit">
      <label for="login-email">邮箱</label>
      <Input
        id="login-email"
        v-model:value="form.email"
        autocomplete="email"
        inputmode="email"
        size="large"
        placeholder="name@example.com"
        :status="validationMessage && !form.email ? 'error' : undefined"
      />

      <label for="login-password">密码</label>
      <InputPassword
        id="login-password"
        v-model:value="form.password"
        autocomplete="current-password"
        size="large"
        placeholder="输入你的密码"
        :status="validationMessage && !form.password ? 'error' : undefined"
      />

      <Button type="primary" html-type="submit" size="large" block :loading="submitting">
        登录
      </Button>
    </form>

    <p class="auth-switch">
      还没有账号？<RouterLink :to="ROUTE_PATHS.register">免费注册</RouterLink>
    </p>
    <p class="auth-security">登录状态只用于识别你的账户和恢复面试进度。</p>
    <p class="auth-legal">
      继续即表示你同意<RouterLink :to="ROUTE_PATHS.terms">用户协议</RouterLink>和
      <RouterLink :to="ROUTE_PATHS.privacy">隐私政策</RouterLink>。
    </p>
  </AuthPanel>
</template>

<style scoped src="./auth-view.css"></style>
