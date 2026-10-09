<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import InterviewTopBar from '@/features/interview/components/InterviewTopBar.vue'
import InterviewQuestionPanel from '@/features/interview/components/InterviewQuestionPanel.vue'
import InterviewAnswerEditor from '@/features/interview/components/InterviewAnswerEditor.vue'
import InterviewWaitingState from '@/features/interview/components/InterviewWaitingState.vue'
import { observeInterviewFlow } from '@/features/interview/api/interview-flow-sse'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { useAuthStore } from '@/features/auth/store/auth-store'
import { toApiError } from '@/shared/api/errors'
const route=useRoute(), router=useRouter(), store=useInterviewFlowStore(), auth=useAuthStore(), id=computed(()=>String(route.params.interviewId))
const answer=ref(''), frozen=ref(false), saving=ref(false), paused=ref(false), remaining=ref(3600)
let draftTimer:number|undefined, waitTimer:number|undefined, clockTimer:number|undefined, stream:AbortController|null=null, hiddenPaused=false
const progress=ref(1)
watch(()=>store.question?.displayOrder,(displayOrder)=>{if(displayOrder!==undefined){progress.value=Math.min(8,Math.max(1,Math.floor(displayOrder/10)))}},{immediate:true})
watch(()=>store.question?.questionId,()=>{ answer.value=store.question?.draft ?? ''; frozen.value=false })
watch(answer,()=>{ if(frozen.value||!store.question)return; window.clearTimeout(draftTimer); draftTimer=window.setTimeout(save,900) })
onMounted(async()=>{ document.addEventListener('visibilitychange',visibility); try{ await store.restore(id.value); if(store.session?.stage!=='INTERVIEWING'){ await routeForStage(); return } remaining.value=store.timer?.remainingSeconds ?? 3600; connect(); startClock(); if(store.waiting) startWaitingPoll() }catch(e){ message.error(toApiError(e).message) } })
onBeforeUnmount(()=>{ document.removeEventListener('visibilitychange',visibility); window.clearTimeout(draftTimer); window.clearInterval(waitTimer); window.clearInterval(clockTimer); stream?.abort() })
async function save(){ if(!store.question)return; saving.value=true; try{ await store.saveDraft(answer.value) }catch(e){ message.error(toApiError(e).message) }finally{ saving.value=false } }
async function submit(){ window.clearTimeout(draftTimer); frozen.value=true; try{ await store.submit(answer.value); paused.value=true; startWaitingPoll(); await routeForStage() }catch(e){ frozen.value=false; message.error(toApiError(e).message) } }
function skip(){ Modal.confirm({title:'跳过当前题？',content:'跳过后不能补答，未考察内容不会被当作弱项。',okText:'确认跳过',async onOk(){ window.clearTimeout(draftTimer); frozen.value=true; try{await store.skip(); paused.value=true; startWaitingPoll(); await routeForStage()}catch(e){frozen.value=false;message.error(toApiError(e).message)}}}) }
function finish(){ Modal.confirm({title:'结束本场面试？',content:'已冻结的回答将用于生成正式报告。',okType:'danger',okText:'结束面试',async onOk(){ try{await store.finish();await routeForStage()}catch(e){message.error(toApiError(e).message)}}}) }
async function togglePause(){ try{ if(paused.value){ await store.resume(); remaining.value=store.timer?.remainingSeconds ?? remaining.value; paused.value=false }else{ await store.pause(); remaining.value=store.timer?.remainingSeconds ?? remaining.value; paused.value=true } }catch(e){message.error(toApiError(e).message)} }
async function visibility(){ if(document.hidden&&!paused.value){ try{await store.pause();paused.value=true;hiddenPaused=true}catch{} }else if(!document.hidden&&hiddenPaused){ try{await store.loadQuestionThenResume();remaining.value=store.timer?.remainingSeconds??remaining.value;paused.value=false;hiddenPaused=false}catch{} } }
function startWaitingPoll(){ window.clearInterval(waitTimer); waitTimer=window.setInterval(async()=>{ try{ const session=await store.restore(id.value); if(session?.stage!=='INTERVIEWING'){window.clearInterval(waitTimer);await routeForStage();return} if(store.question){ await store.loadQuestionThenResume(); paused.value=false; window.clearInterval(waitTimer) } }catch{/* SSE丢失时保留REST轮询 */} },2000) }
function startClock(){ clockTimer=window.setInterval(()=>{ if(!paused.value&&!store.waiting&&remaining.value>0) remaining.value-- },1000) }
function connect(){ const actor=auth.currentUser?.userId;if(!actor)return;stream?.abort();stream=new AbortController();void observeInterviewFlow(actor,id.value,stream.signal,async(_event,name)=>{if(name==='question_ready'){await store.restore(id.value);if(store.question){await store.loadQuestionThenResume();paused.value=false;window.clearInterval(waitTimer)}}else if(name==='report_ready'){await routeForStage()}}).catch(()=>startWaitingPoll()) }
async function routeForStage(){ const stage=store.session?.stage;if(stage==='PREPARING'||stage==='READY') await router.replace(`/app/interviews/${id.value}/ready`);else if(stage==='GENERATING_REPORT'||stage==='COMPLETED') await router.replace(`/app/interviews/${id.value}/report`) }
</script>
<template><section class="session"><InterviewTopBar :remaining-seconds="remaining" :paused="paused || store.waiting" :progress="progress" @pause="togglePause" @finish="finish"/><InterviewWaitingState v-if="store.waiting || !store.question"/><template v-else><InterviewQuestionPanel :question="store.question"/><InterviewAnswerEditor v-model="answer" :frozen="frozen" :saving="saving" @submit="submit" @skip="skip"/></template></section></template>
<style scoped>.session{display:grid;gap:18px;max-width:980px;margin:0 auto}</style>
