<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { Button, Empty, Input, Modal, Select, Spin, Tag, message as toast } from 'ant-design-vue'

import SafeMarkdown from '@/components/content/SafeMarkdown.vue'
import {
  createEvidence,
  EvidenceChangedError,
  loadEvidence,
  loadEvidencePage,
  revokeReviewedEvidence,
  updateReviewedEvidence,
} from '@/features/evidence/api/evidence-api'
import type {
  CreatePersonalEvidenceRequestType,
  PersonalEvidenceListItemResponse,
  PersonalEvidenceResponse,
} from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

const items = ref<PersonalEvidenceListItemResponse[]>([])
const selected = ref<PersonalEvidenceResponse | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const editorOpen = ref(false)
const editing = ref(false)
const typeFilter = ref<CreatePersonalEvidenceRequestType | undefined>()
const type = ref<CreatePersonalEvidenceRequestType>('RESUME')
const sourceName = ref('')
const rawContent = ref('')
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)

const typeOptions = [
  { value: 'RESUME', label: '简历文本' },
  { value: 'PROJECT', label: '项目证据' },
  { value: 'TEST_REPORT', label: '测试报告' },
]

const selectedChunkCount = computed(() => selected.value?.chunks?.length ?? 0)

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    const page = await loadEvidencePage({ limit: 12, type: typeFilter.value })
    items.value = page.items
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
    if (
      selected.value &&
      !items.value.some((item) => item.artifactId === selected.value?.artifactId)
    )
      selected.value = null
  } catch (error) {
    toast.error(error instanceof EvidenceChangedError ? error.message : toApiError(error).message)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) return
  const page = await loadEvidencePage({
    limit: 12,
    type: typeFilter.value,
    cursor: nextCursor.value,
  })
  items.value.push(...page.items)
  nextCursor.value = page.nextCursor
  hasMore.value = page.hasMore
}

async function selectItem(item: PersonalEvidenceListItemResponse) {
  if (!item.artifactId) return
  detailLoading.value = true
  try {
    selected.value = await loadEvidence(item.artifactId)
  } catch (error) {
    toast.error(toApiError(error).message)
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  editing.value = false
  type.value = 'RESUME'
  sourceName.value = ''
  rawContent.value = ''
  editorOpen.value = true
}

function openEdit() {
  if (!selected.value) return
  editing.value = true
  type.value = selected.value.type ?? 'RESUME'
  sourceName.value = selected.value.sourceName ?? ''
  rawContent.value = selected.value.content ?? ''
  editorOpen.value = true
}

async function save() {
  saving.value = true
  try {
    selected.value =
      editing.value && selected.value
        ? await updateReviewedEvidence(selected.value, sourceName.value, rawContent.value)
        : await createEvidence({
            type: type.value,
            sourceName: sourceName.value,
            rawContent: rawContent.value,
          })
    editorOpen.value = false
    toast.success(editing.value ? '已创建新证据版本' : '证据已创建并完成切块')
    await reload()
  } catch (error) {
    if (error instanceof EvidenceChangedError) selected.value = error.latest
    toast.error(toApiError(error).message)
  } finally {
    saving.value = false
  }
}

function revoke() {
  if (!selected.value) return
  Modal.confirm({
    title: '撤销当前 ACTIVE 证据？',
    content: '撤销后将不再出现在面试证据选择中，历史记录仍由后端保留。',
    okText: '确认撤销',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        if (!selected.value) return
        await revokeReviewedEvidence(selected.value)
        selected.value = null
        toast.success('证据已撤销')
        await reload()
      } catch (error) {
        if (error instanceof EvidenceChangedError) selected.value = error.latest
        toast.error(
          error instanceof EvidenceChangedError ? error.message : toApiError(error).message,
        )
      }
    },
  })
}

function typeLabel(value?: string) {
  return typeOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}
</script>

<template>
  <section class="evidence-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow"><FileTextOutlined />贯穿业务的可信证据</p>
        <h2>个人证据库</h2>
        <p>当前只接收文本或 Markdown，不上传文件、不做 PDF/DOCX/OCR 解析。</p>
      </div>
      <div class="actions">
        <Select
          v-model:value="typeFilter"
          allow-clear
          placeholder="全部类型"
          :options="typeOptions"
          @change="reload"
        /><Button :loading="loading" @click="reload"><ReloadOutlined /></Button
        ><Button type="primary" @click="openCreate"><PlusOutlined />新增证据</Button>
      </div>
    </header>

    <div class="workspace">
      <aside class="evidence-list">
        <Spin :spinning="loading">
          <button
            v-for="item in items"
            :key="item.artifactId"
            class="evidence-item"
            :class="{ active: item.artifactId === selected?.artifactId }"
            @click="selectItem(item)"
          >
            <Tag color="blue">{{ typeLabel(item.type) }}</Tag
            ><strong>{{ item.sourceName }}</strong
            ><span>v{{ item.artifactVersion }} · {{ item.updatedAt?.slice(0, 10) }}</span>
          </button>
          <Empty v-if="!loading && !items.length" description="暂无 ACTIVE 个人证据" />
          <Button v-if="hasMore" block @click="loadMore">加载更多</Button>
        </Spin>
      </aside>
      <main class="evidence-detail">
        <Spin :spinning="detailLoading">
          <template v-if="selected">
            <header>
              <div>
                <Tag color="green">{{ selected.status }}</Tag>
                <h3>{{ selected.sourceName }}</h3>
                <p>
                  {{ typeLabel(selected.type) }} · ACTIVE v{{ selected.artifactVersion }} ·
                  {{ selectedChunkCount }} 个切块
                </p>
              </div>
              <div class="actions">
                <Button @click="openEdit"><EditOutlined />创建新版本</Button
                ><Button danger @click="revoke"><DeleteOutlined />撤销</Button>
              </div>
            </header>
            <div class="content-preview"><SafeMarkdown :content="selected.content" /></div>
            <section>
              <h4>切块状态</h4>
              <div class="chunk-grid">
                <article v-for="chunk in selected.chunks" :key="chunk.evidenceChunkId">
                  <strong>#{{ (chunk.chunkIndex ?? 0) + 1 }}</strong
                  ><span>{{ chunk.startOffset }}–{{ chunk.endOffset }}</span>
                  <p>{{ chunk.chunkContent }}</p>
                </article>
              </div>
            </section>
          </template>
          <Empty v-else description="选择一条证据查看内容和切块" />
        </Spin>
      </main>
    </div>

    <Modal
      v-model:open="editorOpen"
      :title="editing ? '创建证据新版本' : '新增个人证据'"
      width="760px"
      :confirm-loading="saving"
      @ok="save"
    >
      <div class="editor-form">
        <label>类型</label
        ><Select v-model:value="type" :disabled="editing" :options="typeOptions" /><label
          >来源名称</label
        ><Input v-model:value="sourceName" :maxlength="255" show-count /><label
          >文本 / Markdown 内容</label
        ><Input.TextArea
          v-model:value="rawContent"
          :maxlength="100000"
          show-count
          :auto-size="{ minRows: 12, maxRows: 22 }"
        />
      </div>
    </Modal>
  </section>
</template>

<style scoped>
.evidence-page {
  display: grid;
  gap: 22px;
}
.page-heading,
.evidence-detail header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}
.page-heading h2 {
  margin: 4px 0 7px;
  color: #0b1740;
  font-size: 32px;
}
.page-heading p {
  margin: 0;
  color: var(--cf-color-text-muted);
}
.eyebrow {
  color: var(--cf-color-primary) !important;
  font-weight: 700;
}
.actions {
  display: flex;
  gap: 9px;
}
.workspace {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 18px;
}
.evidence-list,
.evidence-detail {
  min-height: 560px;
  padding: 18px;
  border: 1px solid var(--cf-color-border);
  border-radius: 15px;
  background: #fff;
  box-shadow: var(--cf-shadow-card);
}
.evidence-list :deep(.ant-spin-container) {
  display: grid;
  gap: 8px;
}
.evidence-item {
  display: grid;
  justify-items: start;
  gap: 7px;
  padding: 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f8fafc;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.evidence-item.active {
  border-color: #a5b4fc;
  background: #eef2ff;
}
.evidence-item span:last-child {
  color: var(--cf-color-text-muted);
  font-size: 12px;
}
.evidence-detail header h3 {
  margin: 12px 0 5px;
  font-size: 23px;
}
.evidence-detail header p {
  margin: 0;
  color: var(--cf-color-text-muted);
}
.content-preview {
  max-height: 360px;
  overflow-y: auto;
  margin: 22px 0;
  padding: 18px;
  border-radius: 12px;
  background: #f8fafc;
}
.chunk-grid {
  display: grid;
  gap: 9px;
}
.chunk-grid article {
  padding: 12px;
  border: 1px solid var(--cf-color-border);
  border-radius: 9px;
}
.chunk-grid span {
  margin-left: 10px;
  color: var(--cf-color-text-muted);
  font-size: 12px;
}
.chunk-grid p {
  margin: 7px 0 0;
  color: var(--cf-color-text-secondary);
  font-size: 13px;
}
.editor-form {
  display: grid;
  gap: 10px;
}
.editor-form label {
  margin-top: 5px;
  font-weight: 650;
}
@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
  }
  .evidence-list {
    min-height: auto;
  }
  .page-heading {
    flex-direction: column;
  }
}
</style>
