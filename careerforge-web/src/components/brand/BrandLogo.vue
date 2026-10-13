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
    aria-label="水水面首页"
  >
    <span class="brand-logo__mark" aria-hidden="true">水</span>
    <span v-if="!compact" class="brand-logo__text">水水面</span>
  </RouterLink>
</template>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ssm-color-ink);
  font-size: 21px;
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1;
  white-space: nowrap;
}

.brand-logo__mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid currentColor;
  border-radius: 8px;
  background: var(--ssm-color-surface);
  color: var(--ssm-color-brand);
  font-family: var(--ssm-font-serif);
  font-size: 20px;
  font-weight: 600;
  line-height: 1;
}

.brand-logo--inverted {
  color: #fff;
}

.brand-logo--inverted .brand-logo__mark {
  border-color: rgba(255, 255, 255, 0.72);
  background: transparent;
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
