import { defineConfig } from 'orval'

const openApiInput = process.env.CAREERFORGE_OPENAPI_INPUT ?? 'http://localhost:8080/v3/api-docs'

export default defineConfig({
  careerforge: {
    input: {
      target: openApiInput,
      override: {
        transformer: './scripts/openapi/frontend-transformer.mjs',
      },
      filters: {
        mode: 'include',
        tags: ['frontend'],
      },
    },
    output: {
      target: './src/shared/api/generated/careerforge.ts',
      schemas: './src/shared/api/generated/model',
      mode: 'single',
      httpClient: 'axios',
      headers: true,
      clean: true,
      formatter: 'prettier',
      override: {
        mutator: {
          path: './src/shared/api/orval-client.ts',
          name: 'orvalClient',
        },
      },
    },
  },
})
