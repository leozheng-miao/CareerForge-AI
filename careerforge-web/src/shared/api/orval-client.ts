import { mergeConfig, type AxiosRequestConfig } from 'axios'

import { httpClient } from './http'

export async function orvalClient<T>(config: AxiosRequestConfig, options?: AxiosRequestConfig) {
  const response = await httpClient.request<T>(mergeConfig(options ?? {}, config))
  return response.data
}

export default orvalClient
