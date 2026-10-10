<script setup lang="ts">
import { CheckCircleFilled, SafetyCertificateOutlined } from '@ant-design/icons-vue'

defineProps<{
  eyebrow: string
  title: string
  description: string
}>()
</script>

<template>
  <section class="auth-panel" :aria-labelledby="`${$attrs.id}-title`">
    <div class="auth-panel__benefits">
      <p class="auth-panel__eyebrow"><SafetyCertificateOutlined />证据驱动的职业成长</p>
      <h1 :id="`${$attrs.id}-title`">从一个清晰目标开始，建立能被验证的能力</h1>
      <p>诊断、训练和面试使用同一套岗位目标与个人证据，避免割裂的功能堆叠。</p>
      <ul>
        <li><CheckCircleFilled />岗位要求与能力差距可追溯</li>
        <li><CheckCircleFilled />训练结果沉淀为个人证据</li>
        <li><CheckCircleFilled />多 Agent 面试反馈驱动下一轮计划</li>
      </ul>
    </div>

    <div class="auth-panel__form-card">
      <p class="auth-panel__eyebrow">{{ eyebrow }}</p>
      <h2>{{ title }}</h2>
      <p class="auth-panel__description">{{ description }}</p>
      <slot />
    </div>
  </section>
</template>

<style scoped>
.auth-panel {
  display: grid;
  min-height: calc(100vh - var(--cf-public-header-height));
  grid-template-columns: minmax(0, 1fr) minmax(420px, 0.82fr);
  align-items: stretch;
  background: #fbfdff;
}

.auth-panel__benefits,
.auth-panel__form-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 80px max(7vw, 48px);
}

.auth-panel__benefits {
  background: #f5f7ff;
}

.auth-panel__benefits h1 {
  max-width: 620px;
  margin: 0;
  color: #0b1740;
  font-size: clamp(36px, 3.5vw, 54px);
  letter-spacing: -0.045em;
  line-height: 1.18;
}

.auth-panel__benefits > p:not(.auth-panel__eyebrow) {
  max-width: 620px;
  margin: 22px 0 0;
  color: var(--cf-color-text-muted);
  font-size: 17px;
  line-height: 1.75;
}

.auth-panel__benefits ul {
  display: grid;
  gap: 14px;
  margin: 34px 0 0;
  padding: 0;
  color: var(--cf-color-text-secondary);
  list-style: none;
}

.auth-panel__benefits li {
  display: flex;
  align-items: center;
  gap: 11px;
}

.auth-panel__benefits li :deep(svg) {
  color: var(--cf-color-success);
}

.auth-panel__eyebrow {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 8px;
  margin: 0 0 20px;
  color: var(--cf-color-primary);
  font-size: 14px;
  font-weight: 750;
}

.auth-panel__form-card {
  max-width: 620px;
  background: #fff;
}

.auth-panel__form-card h2 {
  margin: 0;
  color: #0b1740;
  font-size: 32px;
  letter-spacing: -0.035em;
}

.auth-panel__description {
  margin: 12px 0 28px;
  color: var(--cf-color-text-muted);
  line-height: 1.7;
}

@media (max-width: 960px) {
  .auth-panel {
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .auth-panel__benefits {
    padding-block: 64px;
  }

  .auth-panel__form-card {
    width: 100%;
    max-width: none;
  }
}

@media (max-width: 600px) {
  .auth-panel__benefits,
  .auth-panel__form-card {
    padding: 48px 24px;
  }

  .auth-panel__benefits h1 {
    font-size: 36px;
  }
}
</style>
