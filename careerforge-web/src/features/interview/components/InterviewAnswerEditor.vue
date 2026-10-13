<script setup lang="ts">
import { ref, watch } from 'vue'
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'

const props = withDefaults(defineProps<{
  modelValue: string
  frozen: boolean
  saving: boolean
  paused?: boolean
}>(), { paused: false })
const emit = defineEmits<{ 'update:modelValue': [value: string]; submit: []; skip: [] }>()
const local = ref(props.modelValue)

watch(() => props.modelValue, (value) => {
  if (value !== local.value) local.value = value
})
watch(local, (value) => emit('update:modelValue', value))
</script>

<template>
  <section class="answer-editor" aria-labelledby="answer-editor-title">
    <header>
      <div>
        <p>你的回答</p>
        <h2 id="answer-editor-title">结合真实经历，讲清楚判断和取舍。</h2>
      </div>
      <span v-if="frozen" class="answer-editor__frozen" role="status">
        <CheckCircleOutlined aria-hidden="true" />
        回答已提交并冻结
      </span>
      <span v-else-if="paused" class="answer-editor__paused" role="status">
        面试已暂停，继续后可以作答
      </span>
    </header>
    <textarea
      v-model="local"
      :disabled="frozen || paused"
      maxlength="12000"
      rows="13"
      aria-label="当前问题的回答"
      placeholder="可以从背景、你的做法、为什么这样选择，以及最后结果开始写……"
    />
    <footer>
      <span>{{ saving ? '正在保存草稿…' : `${local.length} / 12000` }}</span>
      <div>
        <Button :disabled="frozen || paused || saving" @click="$emit('skip')">跳过此题</Button>
        <Button type="primary" :disabled="frozen || paused || saving || !local.trim()" @click="$emit('submit')">提交回答</Button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.answer-editor { display: grid; gap: 18px; padding: 30px 0 56px; }
.answer-editor > header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.answer-editor header p { margin: 0 0 7px; color: var(--ssm-color-brand); font-size: 13px; font-weight: 700; }
.answer-editor h2 { margin: 0; color: var(--ssm-color-ink); font-size: 18px; }
.answer-editor__frozen { display: inline-flex; align-items: center; gap: 7px; color: var(--ssm-color-success); font-size: 13px; font-weight: 650; }
.answer-editor__paused { color: var(--ssm-color-warning); font-size: 13px; font-weight: 650; }
.answer-editor textarea { box-sizing: border-box; width: 100%; padding: 18px; border: 1px solid var(--ssm-color-line); border-radius: var(--ssm-radius-control); background: var(--ssm-color-surface); color: var(--ssm-color-ink); font: inherit; font-size: 16px; line-height: 1.75; resize: vertical; }
.answer-editor textarea:focus { border-color: var(--ssm-color-brand); outline: 3px solid color-mix(in srgb, var(--ssm-color-brand) 18%, transparent); }
.answer-editor textarea:disabled { background: var(--ssm-color-paper); color: var(--ssm-color-text-secondary); }
.answer-editor footer, .answer-editor footer div { display: flex; align-items: center; gap: 10px; }
.answer-editor footer { justify-content: space-between; color: var(--ssm-color-text-secondary); font-size: 13px; }
@media (max-width: 600px) { .answer-editor > header, .answer-editor footer { align-items: stretch; flex-direction: column; } .answer-editor footer div { display: grid; grid-template-columns: 1fr 1fr; } }
</style>
