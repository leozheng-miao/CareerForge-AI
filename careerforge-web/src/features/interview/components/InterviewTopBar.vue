<script setup lang="ts">
import { computed } from 'vue'
import { Button, Tag } from 'ant-design-vue'
const props = defineProps<{ remainingSeconds: number; paused: boolean; progress: number }>()
defineEmits<{ pause: []; finish: [] }>()
const time = computed(() => `${String(Math.floor(props.remainingSeconds / 60)).padStart(2, '0')}:${String(props.remainingSeconds % 60).padStart(2, '0')}`)
</script>
<template><header class="top"><div><strong>快速面试</strong><Tag color="blue">{{ progress }} / 8 个考查目标</Tag></div><div class="actions"><span class="timer">{{ time }}</span><Tag v-if="paused" color="orange">已暂停</Tag><Button @click="$emit('pause')">{{ paused ? '继续' : '暂停' }}</Button><Button danger @click="$emit('finish')">结束面试</Button></div></header></template>
<style scoped>.top,.actions,.top>div{display:flex;align-items:center;gap:12px}.top{justify-content:space-between;padding:16px 20px;border:1px solid var(--cf-color-border);border-radius:14px;background:#fff}.timer{font-variant-numeric:tabular-nums;font-size:24px;font-weight:750;color:#0b1740}</style>
