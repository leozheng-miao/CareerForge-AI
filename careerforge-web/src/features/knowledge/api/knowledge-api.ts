import { queryCareerKnowledge } from '@/shared/api/generated/careerforge'
import type { RagQueryRequest, RagQueryResponse } from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/errors'

export async function askCareerKnowledge(request: RagQueryRequest): Promise<RagQueryResponse> {
  const query = request.query.trim()
  if (!query) throw new ApiError({ kind: 'validation', message: '请输入想查证的职业问题' })
  const response = await queryCareerKnowledge({ ...request, query })
  if (!response.data) throw new ApiError({ kind: 'unknown', message: '知识问答响应缺少数据' })
  return response.data
}
