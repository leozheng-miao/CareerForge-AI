<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Button, Tag, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { getResumableInterview } from '@/features/interview/api/interview-flow-api'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { toApiError } from '@/shared/api/errors'
const router=useRouter(), store=useInterviewFlowStore(); const activeId=ref<string|null>(null), creating=ref(false)
onMounted(async()=>{ try{ activeId.value=(await getResumableInterview())?.interviewId ?? null }catch{/* 保留手动重试 */} })
async function start(){ if(activeId.value){ message.warning('你还有一场未完成的面试'); return } creating.value=true; try{ const value=await store.createQuick(); await router.push(`/app/interviews/${value.interviewId}/ready`) }catch(e){ message.error(toApiError(e).message) }finally{ creating.value=false } }
function continueActive(){ if(activeId.value) void router.push(`/app/interviews/${activeId.value}/session`) }
</script>
<template><section class="start"><header><p class="eyebrow">CAREERFORGE INTERVIEW</p><h2>选择面试模式</h2><p>首版仅支持文字作答，有效作答时长固定 60 分钟。</p></header><div v-if="activeId" class="active"><strong>你还有一场未完成的面试</strong><Button type="primary" @click="continueActive">继续面试</Button></div><div class="cards"><article class="card"><Tag color="blue">首发可用</Tag><h3>快速面试</h3><p>AI Agent 开发方向 · 8 个考查目标 · 最多 4 次追问</p><Button type="primary" size="large" :loading="creating" :disabled="Boolean(activeId)" @click="start">开始准备</Button></article><article class="card disabled"><Tag>第二交付</Tag><h3>综合面试</h3><p>将在快速面试验收后增量开发，本版不可用。</p><Button disabled>暂未开放</Button></article></div></section></template>
<style scoped>.start{display:grid;gap:28px}.eyebrow{color:var(--cf-color-primary);font-weight:750}.start h2{margin:4px 0;font-size:34px;color:#0b1740}.start header>p:last-child,.card p{color:var(--cf-color-text-muted)}.cards{display:grid;grid-template-columns:1fr 1fr;gap:22px}.card,.active{padding:28px;border:1px solid var(--cf-color-border);border-radius:18px;background:#fff;box-shadow:var(--cf-shadow-card)}.card h3{font-size:25px}.card p{min-height:52px;line-height:1.7}.disabled{opacity:.7}.active{display:flex;align-items:center;justify-content:space-between;border-color:#a5b4fc;background:#eef2ff}</style>
