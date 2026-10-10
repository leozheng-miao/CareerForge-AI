import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export const createViteConfig = (mode: string) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendProxyTarget = env.VITE_BACKEND_PROXY_TARGET?.trim() || 'http://127.0.0.1:8080'

  return {
    plugins: [vue(), vueDevTools()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: backendProxyTarget,
          changeOrigin: false,
        },
      },
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => createViteConfig(mode))
