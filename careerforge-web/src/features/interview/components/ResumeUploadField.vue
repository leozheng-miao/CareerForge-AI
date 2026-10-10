<script setup lang="ts">
import { ref } from 'vue'
import { Button, message } from 'ant-design-vue'
import { parseResume, type ConfirmedProfile } from '@/features/interview/api/interview-flow-api'
import { toApiError } from '@/shared/api/errors'

const emit = defineEmits<{ parsed: [profile: ConfirmedProfile]; failed: [] }>()
const input = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const fileName = ref('')

async function selected(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'docx'].includes(extension ?? '')) {
    message.error('仅支持 PDF 或 DOCX 文件')
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  loading.value = true
  fileName.value = file.name
  try {
    emit('parsed', await parseResume(file))
    message.success('简历已解析，请检查并确认画像')
  } catch (error) {
    emit('failed')
    message.error(toApiError(error).message)
  } finally {
    loading.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}
</script>

<template>
  <div class="upload-field">
    <input ref="input" class="hidden" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" @change="selected">
    <Button :loading="loading" @click="input?.click()">选择 PDF / DOCX 简历</Button>
    <span v-if="fileName">{{ fileName }}</span>
    <small>文件仅用于本次解析，不在浏览器持久化；你也可以跳过简历。</small>
  </div>
</template>

<style scoped>.upload-field{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.hidden{display:none}.upload-field small{width:100%;color:var(--cf-color-text-muted)}</style>
