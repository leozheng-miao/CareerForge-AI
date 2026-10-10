import { createApp } from 'vue'

import App from './App.vue'
import { pinia } from './app/pinia'
import { useAuthStore } from './features/auth/store/auth-store'
import { installAuthGuard } from './router/auth-guard'
import router from './router'
import { registerAuthSessionAdapter } from './shared/api/session-bridge'
import 'ant-design-vue/dist/reset.css'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)
const authStore = useAuthStore(pinia)

registerAuthSessionAdapter({
  getAccessToken: () => authStore.accessToken,
  refreshAccessToken: () => authStore.refreshAccessToken(),
  clearSession: () => authStore.clearSession(),
})
installAuthGuard(router, pinia)

app.use(pinia)
app.use(router)

app.mount('#app')
