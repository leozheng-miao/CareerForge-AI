<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Alert, Button, Input, InputPassword } from 'ant-design-vue'
import { RouterLink, useRouter } from 'vue-router'

import AuthPanel from '@/features/auth/components/AuthPanel.vue'
import { useAuthStore } from '@/features/auth/store/auth-store'
import { ROUTE_PATHS } from '@/router/paths'
import { toApiError } from '@/shared/api/errors'

const router = useRouter()
const authStore = useAuthStore()
const form = reactive({ email: '', displayName: '', password: '', confirmPassword: '' })
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
    await authStore.register({
      email: form.email.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    await router.replace(ROUTE_PATHS.interviews)
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    submitting.value = false
  }
}

function validate() {
  if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) return '请输入有效的邮箱地址'
  if (!form.displayName.trim()) return '请输入用于页面展示的名称'
  if (form.displayName.trim().length > 80) return '展示名称不能超过 80 个字符'
  if (form.password.length < 8 || form.password.length > 72) return '密码长度应为 8 至 72 个字符'
  if (form.password !== form.confirmPassword) return '两次输入的密码不一致'
  return ''
}
</script>

<template>
  <AuthPanel
    id="register"
    eyebrow="开始第一场面试"
    title="创建账户"
    description="注册后可以开始面试，并在中断后恢复进度。"
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
      <label for="register-email">邮箱</label>
      <Input
        id="register-email"
        v-model:value="form.email"
        autocomplete="email"
        inputmode="email"
        size="large"
        placeholder="name@example.com"
      />

      <label for="register-name">展示名称</label>
      <Input
        id="register-name"
        v-model:value="form.displayName"
        autocomplete="name"
        :maxlength="80"
        size="large"
        placeholder="例如：Leo"
      />

      <label for="register-password">密码</label>
      <InputPassword
        id="register-password"
        v-model:value="form.password"
        autocomplete="new-password"
        size="large"
        placeholder="8 至 72 个字符"
      />

      <label for="register-confirm-password">确认密码</label>
      <InputPassword
        id="register-confirm-password"
        v-model:value="form.confirmPassword"
        autocomplete="new-password"
        size="large"
        placeholder="再次输入密码"
      />

      <Button type="primary" html-type="submit" size="large" block :loading="submitting">
        创建账户
      </Button>
    </form>

    <p class="auth-switch">已有账号？<RouterLink :to="ROUTE_PATHS.login">直接登录</RouterLink></p>
    <p class="auth-security">请只提交你有权处理的岗位和个人材料。</p>
    <p class="auth-legal">
      注册即表示你同意<RouterLink :to="ROUTE_PATHS.terms">用户协议</RouterLink>和
      <RouterLink :to="ROUTE_PATHS.privacy">隐私政策</RouterLink>。
    </p>
  </AuthPanel>
</template>

<style scoped src="./auth-view.css"></style>
