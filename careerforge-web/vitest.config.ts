import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import { createViteConfig } from './vite.config.ts'

export default defineConfig(({ mode }) =>
  mergeConfig(
    createViteConfig(mode),
    defineConfig({
      test: {
        environment: 'jsdom',
        exclude: [...configDefaults.exclude, 'e2e/**', 'scripts/openapi/**'],
        root: fileURLToPath(new URL('./', import.meta.url)),
        setupFiles: ['./src/__tests__/setup.ts'],
      },
    }),
  ),
)
