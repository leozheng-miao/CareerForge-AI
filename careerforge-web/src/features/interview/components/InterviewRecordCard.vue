<script setup lang="ts">
import { Button, Tag } from 'ant-design-vue'; import type { InterviewRecord } from '@/features/interview/api/interview-flow-api'
defineProps<{ record: InterviewRecord }>(); defineEmits<{ open:[record:InterviewRecord] }>()
const label=(stage:string)=>({PREPARING:'准备中',READY:'待开始',INTERVIEWING:'进行中',GENERATING_REPORT:'报告生成中',COMPLETED:'已完成',FAILED:'未完成'}[stage]??stage)
</script>
<template><article class="record"><div><Tag color="blue">{{ record.mode==='COMPREHENSIVE' ? '综合面试' : '快速面试' }}</Tag><Tag>{{ label(record.stage) }}</Tag></div><h3>AI Agent 开发方向</h3><p>{{ new Date(record.createdAt).toLocaleString() }} · 已提交 {{ record.submittedAnswerCount }} 个回答</p><Button type="primary" @click="$emit('open',record)">{{ record.stage==='INTERVIEWING' ? '继续面试' : record.stage==='READY' ? '进入面试' : record.stage==='PREPARING' ? '查看准备进度' : '查看报告' }}</Button></article></template>
<style scoped>.record{padding:22px;border:1px solid var(--cf-color-border);border-radius:15px;background:#fff;box-shadow:var(--cf-shadow-card)}h3{margin:14px 0 8px;color:#0b1740}p{color:var(--cf-color-text-muted)}</style>
