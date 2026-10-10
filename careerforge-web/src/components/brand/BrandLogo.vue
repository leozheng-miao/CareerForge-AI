<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { ROUTE_PATHS } from '@/router/paths'

withDefaults(
  defineProps<{
    compact?: boolean
    inverted?: boolean
  }>(),
  {
    compact: false,
    inverted: false,
  },
)
</script>

<template>
  <RouterLink
    class="brand-logo"
    :class="{ 'brand-logo--inverted': inverted }"
    :to="ROUTE_PATHS.home"
    aria-label="CareerForge-AI 首页"
  >
    <svg class="brand-logo__mark" viewBox="0 0 40 40" aria-hidden="true">
      <path d="M20 2 36 11v18L20 38 4 29V11L20 2Z" fill="#eef2ff" />
      <path d="m20 2 16 9-7.8 4.5L20 11l-8.2 4.7L4 11 20 2Z" fill="#4f46e5" />
      <path d="M4 11 11.8 15.7v9.1L20 29.5V38L4 29V11Z" fill="#1e3a8a" />
      <path d="m36 11-7.8 4.5v9.3L20 29.5V38l16-9V11Z" fill="#6366f1" />
      <path d="m11.8 15.7 8.2-4.7 8.2 4.5v9.3L20 29.5l-8.2-4.7v-9.1Z" fill="#fff" />
    </svg>
    <span v-if="!compact" class="brand-logo__text">CareerForge-AI</span>
  </RouterLink>
</template>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-color-text);
  font-size: 21px;
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1;
  white-space: nowrap;
}

.brand-logo__mark {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
}

.brand-logo--inverted {
  color: #fff;
}

@media (max-width: 479px) {
  .brand-logo {
    gap: 8px;
    font-size: 18px;
  }

  .brand-logo__mark {
    width: 32px;
    height: 32px;
  }
}
</style>
