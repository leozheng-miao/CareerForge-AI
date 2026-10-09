<script setup lang="ts">import { Spin } from 'ant-design-vue'</script>
<template><section class="waiting"><Spin size="large"/><h2>面试官正在整理你的回答并准备下一题…</h2><p>等待期间不计入 60 分钟有效作答时间。</p></section></template>
<style scoped>.waiting{display:grid;place-items:center;min-height:360px;padding:40px;text-align:center;border:1px solid var(--cf-color-border);border-radius:16px;background:#fff}.waiting h2{margin:24px 0 8px;color:#0b1740}.waiting p{color:var(--cf-color-text-muted)}</style>
