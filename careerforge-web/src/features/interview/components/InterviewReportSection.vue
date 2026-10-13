<script setup lang="ts">
defineProps<{
  index?: string
  title: string
  items: Array<{
    title: string
    description: string
    quote?: string
  }>
}>()
</script>

<template>
  <section class="report-section">
    <p v-if="index" class="report-section__index">{{ index }}</p>
    <h2>{{ title }}</h2>
    <div v-if="items.length" class="report-section__items">
      <article v-for="(item, itemIndex) in items" :key="item.title + '-' + itemIndex">
        <h3>{{ item.title }}</h3>
        <p>{{ item.description }}</p>
        <blockquote v-if="item.quote">{{ item.quote }}</blockquote>
      </article>
    </div>
    <p v-else class="report-section__empty">暂无需要单独列出的内容。</p>
  </section>
</template>

<style scoped>
.report-section {
  padding: clamp(24px, 4vw, 40px);
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-panel);
  background: var(--ssm-color-surface);
}

.report-section__index {
  margin: 0 0 var(--ssm-space-2);
  color: var(--ssm-color-brand);
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.1em;
}

.report-section > h2 {
  margin: 0 0 var(--ssm-space-6);
  color: var(--ssm-color-ink);
  font-family: var(--ssm-font-serif);
  font-size: 28px;
}

.report-section__items {
  display: grid;
  gap: var(--ssm-space-6);
}

.report-section article + article {
  padding-top: var(--ssm-space-6);
  border-top: 1px solid var(--ssm-color-line);
}

.report-section h3 {
  margin: 0 0 var(--ssm-space-2);
  color: var(--ssm-color-ink);
  font-size: 17px;
  line-height: 1.55;
}

.report-section article > p {
  margin: 0;
  color: var(--ssm-color-text-secondary);
  line-height: 1.8;
  white-space: pre-wrap;
}

.report-section blockquote {
  margin: var(--ssm-space-4) 0 0;
  padding: var(--ssm-space-4) var(--ssm-space-6);
  border-left: 3px solid var(--ssm-color-brand);
  background: var(--ssm-color-brand-soft);
  color: var(--ssm-color-ink);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.report-section__empty {
  margin: 0;
  color: var(--ssm-color-text-muted);
  line-height: 1.75;
}

@media (max-width: 680px) {
  .report-section {
    padding: var(--ssm-space-6);
  }

  .report-section blockquote {
    padding-inline: var(--ssm-space-4);
  }
}
</style>
