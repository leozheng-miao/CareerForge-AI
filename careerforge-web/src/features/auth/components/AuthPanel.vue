<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description: string
}>()
</script>

<template>
  <section class="auth-panel" :aria-labelledby="`${$attrs.id}-title`">
    <header class="auth-panel__header">
      <p>{{ eyebrow }}</p>
      <h1 :id="`${$attrs.id}-title`">{{ title }}</h1>
      <span>{{ description }}</span>
    </header>
    <slot />
  </section>
</template>

<style scoped>
.auth-panel {
  width: min(calc(100% - 40px), 460px);
  margin: 0 auto;
  padding: 72px 0 96px;
}

.auth-panel__header {
  margin-bottom: 34px;
}

.auth-panel__header p {
  margin: 0 0 14px;
  color: var(--ssm-color-brand);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.06em;
}

.auth-panel__header h1 {
  margin: 0;
  font-family: var(--ssm-font-serif);
  font-size: 46px;
  font-weight: 600;
  letter-spacing: -0.045em;
}

.auth-panel__header span {
  display: block;
  margin-top: 14px;
  color: var(--ssm-color-text-secondary);
  line-height: 1.7;
}

@media (max-width: 600px) {
  .auth-panel {
    padding-top: 54px;
  }

  .auth-panel__header h1 {
    font-size: 40px;
  }
}
</style>
