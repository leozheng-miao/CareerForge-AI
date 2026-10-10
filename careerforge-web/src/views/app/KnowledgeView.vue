<script setup lang="ts">
import { ref } from 'vue'
import { BookOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Button, Checkbox, Input, Tag, message as toast } from 'ant-design-vue'

import SafeMarkdown from '@/components/content/SafeMarkdown.vue'
import { askCareerKnowledge } from '@/features/knowledge/api/knowledge-api'
import type {
  RagQueryRequestDocumentTypesItem,
  RagQueryResponse,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

const query = ref('')
const documentTypes = ref<RagQueryRequestDocumentTypesItem[]>([
  'JOB_DESCRIPTION',
  'INTERVIEW_EXPERIENCE',
])
const documentIdsText = ref('')
const answer = ref<RagQueryResponse | null>(null)
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const documentIds = [
      ...new Set(
        documentIdsText.value
          .split(/[\n,]/)
          .map((item) => item.trim())
          .filter(Boolean),
      ),
    ]
    if (documentIds.length > 20) throw new Error('文档 ID 最多 20 个')
    answer.value = await askCareerKnowledge({
      query: query.value,
      documentTypes: documentTypes.value,
      documentIds,
    })
  } catch (error) {
    toast.error(
      error instanceof Error && error.message === '文档 ID 最多 20 个'
        ? error.message
        : toApiError(error).message,
    )
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="knowledge-page">
    <header class="page-heading">
      <p class="eyebrow"><BookOutlined />可信 RAG 问答</p>
      <h2>从职业知识库查证，而不是让模型猜测</h2>
      <p>答案必须带合法引用；检索证据不足时，页面明确展示拒答状态。</p>
    </header>

    <div class="query-card">
      <label for="knowledge-query">问题</label>
      <Input.TextArea
        id="knowledge-query"
        v-model:value="query"
        :maxlength="2000"
        show-count
        :auto-size="{ minRows: 3, maxRows: 7 }"
        placeholder="例如：AI Agent 应用开发岗位通常要求哪些工程能力？"
      />
      <div class="scope-row">
        <div>
          <span class="scope-label">检索范围</span>
          <Checkbox.Group v-model:value="documentTypes">
            <Checkbox value="JOB_DESCRIPTION">岗位描述</Checkbox>
            <Checkbox value="INTERVIEW_EXPERIENCE">面试经验</Checkbox>
          </Checkbox.Group>
        </div>
        <div class="ids-field">
          <label for="document-ids">可选文档 ID（逗号或换行分隔）</label>
          <Input
            id="document-ids"
            v-model:value="documentIdsText"
            placeholder="留空表示使用当前知识库范围"
          />
        </div>
      </div>
      <Button
        type="primary"
        size="large"
        :loading="loading"
        :disabled="!query.trim()"
        @click="submit"
        ><SearchOutlined />查证并回答</Button
      >
    </div>

    <article v-if="answer" class="answer-card">
      <div class="answer-meta">
        <Tag :color="answer.status === 'ANSWERED' ? 'green' : 'orange'">{{
          answer.status === 'ANSWERED' ? '已基于证据回答' : '证据不足，已拒答'
        }}</Tag>
        <span
          >候选 {{ answer.retrievedCandidateCount ?? 0 }} · 上下文
          {{ answer.contextChunkCount ?? 0 }} · {{ answer.totalDurationMs ?? 0 }}ms</span
        >
      </div>
      <SafeMarkdown :content="answer.answer" />
      <section class="citations" aria-label="答案引用">
        <h3>引用依据（{{ answer.citations?.length ?? 0 }}）</h3>
        <div v-if="answer.citations?.length" class="citation-grid">
          <article
            v-for="(citation, index) in answer.citations"
            :key="citation.chunkId || index"
            class="citation"
          >
            <strong
              >[{{ index + 1 }}]
              {{ citation.documentName || citation.documentId || '未命名文档' }}</strong
            >
            <span>{{ citation.documentType }} · chunk {{ citation.chunkIndex ?? '-' }}</span>
            <p v-if="citation.sectionPath?.length">{{ citation.sectionPath.join(' / ') }}</p>
            <code>{{ citation.chunkId }}</code>
          </article>
        </div>
        <p v-else class="no-citations">没有可验证引用；不要把当前回答当作事实依据。</p>
      </section>
    </article>
  </section>
</template>

<style scoped>
.knowledge-page {
  display: grid;
  gap: 24px;
  max-width: 980px;
  margin-inline: auto;
}
.page-heading {
  text-align: center;
}
.page-heading h2 {
  margin: 7px 0 10px;
  color: #0b1740;
  font-size: 32px;
}
.page-heading > p:last-child {
  margin: 0;
  color: var(--cf-color-text-muted);
}
.eyebrow {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--cf-color-primary);
  font-weight: 700;
}
.query-card,
.answer-card {
  padding: 26px;
  border: 1px solid var(--cf-color-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--cf-shadow-card);
}
.query-card {
  display: grid;
  gap: 14px;
}
.query-card > label,
.scope-label,
.ids-field label {
  font-weight: 650;
}
.scope-row {
  display: grid;
  grid-template-columns: 0.8fr 1.2fr;
  gap: 22px;
}
.scope-row > div {
  display: grid;
  gap: 8px;
}
.answer-card {
  display: grid;
  gap: 22px;
}
.answer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}
.citations {
  padding-top: 18px;
  border-top: 1px solid var(--cf-color-border);
}
.citations h3 {
  margin: 0 0 14px;
}
.citation-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.citation {
  display: grid;
  gap: 5px;
  padding: 14px;
  border-radius: 10px;
  background: #f8fafc;
}
.citation span,
.citation p,
.citation code {
  margin: 0;
  color: var(--cf-color-text-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.no-citations {
  color: var(--cf-color-warning);
}
@media (max-width: 720px) {
  .scope-row,
  .citation-grid {
    grid-template-columns: 1fr;
  }
  .answer-meta {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
