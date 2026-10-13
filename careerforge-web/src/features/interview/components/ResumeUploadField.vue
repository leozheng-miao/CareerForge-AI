<script setup lang="ts">
import { ref } from 'vue'
import { CheckCircleOutlined, FileTextOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { Alert, Button } from 'ant-design-vue'

import { parseResume, type ConfirmedProfile } from '@/features/interview/api/interview-flow-api'
import { toApiError } from '@/shared/api/errors'

const props = withDefaults(defineProps<{ confirmReplace?: boolean }>(), {
  confirmReplace: false,
})
const emit = defineEmits<{ parsed: [profile: ConfirmedProfile]; failed: [] }>()
const input = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const fileName = ref('')
const errorMessage = ref('')
const parsedSuccessfully = ref(false)

function chooseFile() {
  if (
    props.confirmReplace
    && !window.confirm('新简历的解析结果会替换当前画像，是否继续选择文件？')
  ) return
  input.value?.click()
}

async function selected(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'docx'].includes(extension ?? '')) {
    errorMessage.value = '仅支持 PDF 或 DOCX 文件'
    parsedSuccessfully.value = false
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  loading.value = true
  fileName.value = file.name
  errorMessage.value = ''
  parsedSuccessfully.value = false
  try {
    emit('parsed', await parseResume(file))
    parsedSuccessfully.value = true
  } catch (error) {
    emit('failed')
    errorMessage.value = toApiError(error).message
  } finally {
    loading.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}
</script>

<template>
  <div class="upload-field">
    <input
      ref="input"
      class="upload-field__input"
      type="file"
      accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      aria-describedby="resume-upload-help"
      @change="selected"
    >
    <div class="upload-field__action">
      <Button size="large" :loading="loading" @click="chooseFile">
        <UploadOutlined aria-hidden="true" />
        {{ fileName ? '重新选择简历' : '选择 PDF / DOCX 简历' }}
      </Button>
      <span v-if="fileName" class="upload-field__file">
        <FileTextOutlined aria-hidden="true" />
        {{ fileName }}
      </span>
    </div>

    <p id="resume-upload-help">
      文件仅用于当次解析，不作为长期个人档案保存。不上传简历也可以继续。
    </p>

    <Alert
      v-if="errorMessage"
      type="error"
      show-icon
      :message="errorMessage"
      description="已填写的岗位和个人信息不会被清空，可以重新选择文件或直接继续。"
    />
    <div v-else-if="parsedSuccessfully" class="upload-field__success" role="status">
      <CheckCircleOutlined aria-hidden="true" />
      简历已解析，请检查下方画像后再继续。
    </div>
  </div>
</template>

<style scoped>
.upload-field {
  display: grid;
  gap: 14px;
}

.upload-field__input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.upload-field__action {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.upload-field__file {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  color: var(--ssm-color-ink);
  font-size: 14px;
}

.upload-field p {
  margin: 0;
  color: var(--ssm-color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.upload-field__success {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--ssm-color-success) 30%, transparent);
  border-radius: var(--ssm-radius-control);
  background: #e4f1e9;
  color: var(--ssm-color-success);
  font-size: 14px;
}
</style>
