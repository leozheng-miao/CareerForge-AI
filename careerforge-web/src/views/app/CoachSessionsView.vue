<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ArrowRightOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { Button, Empty, Input, Modal, Spin, Tag, message as toast } from 'ant-design-vue'
import { useRouter } from 'vue-router'

import { createCoachSession, loadCoachSessions } from '@/features/coach/api/coach-api'
import type { CoachingSessionResponse } from '@/shared/api/generated/model'
import { toApiError } from '@/shared/api/errors'

const router = useRouter()
const sessions = ref<CoachingSessionResponse[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const creating = ref(false)
const createOpen = ref(false)
const title = ref('本周职业成长复盘')
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const pageError = ref('')

onMounted(reload)

async function reload() {
  loading.value = true
  pageError.value = ''
  try {
    const page = await loadCoachSessions()
    sessions.value = page.items
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (error) {
    pageError.value = toApiError(error).message
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) return
  loadingMore.value = true
  try {
    const page = await loadCoachSessions(nextCursor.value)
    sessions.value.push(...page.items)
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (error) {
    toast.error(toApiError(error).message)
  } finally {
    loadingMore.value = false
  }
}

async function createSession() {
  creating.value = true
  try {
    const session = await createCoachSession(title.value)
    createOpen.value = false
    toast.success('会话已创建')
    if (session.sessionId) await router.push(`/app/coach/${session.sessionId}`)
  } catch (error) {
    toast.error(toApiError(error).message)
  } finally {
    creating.value = false
  }
}

function openSession(session: CoachingSessionResponse) {
  if (session.sessionId) void router.push(`/app/coach/${session.sessionId}`)
}

function formatTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '-'
}
</script>

<template>
  <section class="coach-list">
    <header class="page-heading">
      <div>
        <p class="eyebrow"><MessageOutlined />个性化成长训练</p>
        <h2>Career Coach</h2>
        <p>围绕目标岗位、能力差距与已确认记忆持续复盘。回答只展示服务端确认保存的 Turn。</p>
      </div>
      <div class="heading-actions">
        <Button :loading="loading" @click="reload"><ReloadOutlined />刷新</Button>
        <Button type="primary" @click="createOpen = true"><PlusOutlined />新建会话</Button>
      </div>
    </header>

    <div v-if="pageError" class="notice notice--error">{{ pageError }}</div>
    <Spin :spinning="loading">
      <div v-if="sessions.length" class="session-grid">
        <button
          v-for="session in sessions"
          :key="session.sessionId"
          class="session-card"
          @click="openSession(session)"
        >
          <div class="session-card__top">
            <span class="session-icon"><MessageOutlined /></span>
            <Tag :color="session.status === 'ACTIVE' ? 'green' : 'default'">{{
              session.status === 'ACTIVE' ? '进行中' : '已关闭'
            }}</Tag>
          </div>
          <h3>{{ session.title || '未命名会话' }}</h3>
          <p>最近更新：{{ formatTime(session.updatedAt) }}</p>
          <span class="session-card__link">继续对话 <ArrowRightOutlined /></span>
        </button>
      </div>
      <Empty v-else-if="!loading" description="还没有 Career Coach 会话" />
    </Spin>
    <div v-if="hasMore" class="load-more">
      <Button :loading="loadingMore" @click="loadMore">加载更多</Button>
    </div>

    <Modal
      v-model:open="createOpen"
      title="新建 Career Coach 会话"
      :confirm-loading="creating"
      @ok="createSession"
    >
      <label class="field-label" for="coach-title">会话标题</label>
      <Input
        id="coach-title"
        v-model:value="title"
        :maxlength="120"
        show-count
        @press-enter="createSession"
      />
    </Modal>
  </section>
</template>

<style scoped>
.coach-list {
  display: grid;
  gap: 24px;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}
.page-heading h2 {
  margin: 4px 0 8px;
  font-size: 32px;
  color: #0b1740;
}
.page-heading p {
  margin: 0;
  color: var(--cf-color-text-muted);
}
.eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-color-primary) !important;
  font-weight: 700;
}
.heading-actions {
  display: flex;
  gap: 10px;
}
.session-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.session-card {
  min-height: 210px;
  padding: 22px;
  border: 1px solid var(--cf-color-border);
  border-radius: 16px;
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;
  box-shadow: var(--cf-shadow-card);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease;
}
.session-card:hover {
  transform: translateY(-2px);
  border-color: #a5b4fc;
}
.session-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.session-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 12px;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-size: 18px;
}
.session-card h3 {
  margin: 24px 0 8px;
  font-size: 19px;
}
.session-card p {
  color: var(--cf-color-text-muted);
  font-size: 13px;
}
.session-card__link {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 28px;
  color: var(--cf-color-primary);
  font-weight: 650;
}
.notice {
  padding: 12px 14px;
  border-radius: 10px;
}
.notice--error {
  background: #fef2f2;
  color: #b91c1c;
}
.load-more {
  display: flex;
  justify-content: center;
}
.field-label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
}
@media (max-width: 900px) {
  .session-grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 640px) {
  .page-heading {
    flex-direction: column;
  }
  .session-grid {
    grid-template-columns: 1fr;
  }
}
</style>
