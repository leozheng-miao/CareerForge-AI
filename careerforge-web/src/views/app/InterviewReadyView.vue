<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { Button, Spin, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { toApiError } from '@/shared/api/errors'
const route=useRoute(), router=useRouter(), store=useInterviewFlowStore(), id=computed(()=>String(route.params.interviewId)); let poll:number|undefined
const ready=computed(()=>store.session?.stage==='READY')
onMounted(async()=>{ await refresh(); poll=window.setInterval(refresh,2000) }); onBeforeUnmount(()=>window.clearInterval(poll))
async function refresh(){ try{ await store.restore(id.value); if(ready.value) window.clearInterval(poll) }catch(e){ message.error(toApiError(e).message); window.clearInterval(poll) } }
async function enter(){ try{ await store.enter(); await router.replace(`/app/interviews/${id.value}/session`) }catch(e){ message.error(toApiError(e).message) } }
</script>
<template><section class="ready"><Spin :spinning="!ready"><div class="panel"><p class="eyebrow">{{ ready ? '面试已就绪' : '平台正在准备面试' }}</p><h2>{{ ready ? '准备好后，由你确认正式开始' : '正在从公共题库准备本场题目…' }}</h2><p>60 分钟只计算有效作答时间；分析下一题时自动暂停。</p><div><Button @click="router.push('/app/interviews')">返回</Button><Button type="primary" size="large" :disabled="!ready" @click="enter">正式开始</Button></div></div></Spin></section></template>
<style scoped>.ready{display:grid;place-items:center;min-height:65vh}.panel{max-width:720px;padding:48px;border:1px solid var(--cf-color-border);border-radius:20px;background:#fff;text-align:center;box-shadow:var(--cf-shadow-card)}.eyebrow{color:var(--cf-color-primary);font-weight:750}.panel h2{font-size:30px;color:#0b1740}.panel p{line-height:1.8;color:var(--cf-color-text-muted)}.panel div{display:flex;justify-content:center;gap:12px;margin-top:26px}</style>
