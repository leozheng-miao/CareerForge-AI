<script setup lang="ts">
import { computed } from 'vue'

import { renderSafeMarkdown } from '@/shared/markdown/render-markdown'

const props = defineProps<{ content?: string }>()
const html = computed(() => renderSafeMarkdown(props.content))
</script>

<template>
  <div class="safe-markdown" v-html="html" />
</template>

<style scoped>
.safe-markdown {
  overflow-wrap: anywhere;
  color: var(--cf-color-text);
  line-height: 1.75;
}

.safe-markdown :deep(p:first-child) {
  margin-top: 0;
}

.safe-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.safe-markdown :deep(pre) {
  overflow-x: auto;
  padding: 14px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
}

.safe-markdown :deep(code) {
  padding: 2px 5px;
  border-radius: 5px;
  background: #eef2ff;
  color: #3730a3;
}

.safe-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.safe-markdown :deep(a) {
  color: var(--cf-color-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.safe-markdown :deep(blockquote) {
  margin-inline: 0;
  padding-left: 14px;
  border-left: 3px solid #c7d2fe;
  color: var(--cf-color-text-secondary);
}
</style>
