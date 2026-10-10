<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button, Select, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import ResumeUploadField from '@/features/interview/components/ResumeUploadField.vue'
import ProfileDraftEditor from '@/features/interview/components/ProfileDraftEditor.vue'
import InterviewSettingsPanel from '@/features/interview/components/InterviewSettingsPanel.vue'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import type { ComprehensiveSetup, ConfirmedProfile, InterviewDifficulty } from '@/features/interview/api/interview-flow-api'
import { toApiError } from '@/shared/api/errors'

const route = useRoute(), router = useRouter(), store = useInterviewFlowStore()
const existingId = computed(() => typeof route.query.interviewId === 'string' ? route.query.interviewId : null)
const previous = store.comprehensiveSetup
const jobDescription = ref(previous?.jobDescription ?? '')
const salaryOptions = [
  '10K/月以下', '10K-15K/月', '15K-20K/月', '20K-30K/月',
  '30K-40K/月', '40K-60K/月', '60K-80K/月', '80K/月以上',
].map(value => ({ label: value, value }))
const salaryRange = ref<string | undefined>(previous?.salaryRange || undefined)
const personalDescription = ref(previous?.personalDescription ?? '')
const profile = ref<ConfirmedProfile>(previous?.confirmedProfile ?? { personalSummary: '', coreSkills: [], projectExperiences: [], workExperiences: [], internshipExperiences: [] })
const durationSeconds = ref<3600 | 5400>(previous?.durationSeconds ?? 3600)
const difficulty = ref<InterviewDifficulty>(previous?.difficulty ?? 'STANDARD')
const submitting = ref(false)
function hasProfile(value: ConfirmedProfile) { return Boolean(value.personalSummary || value.coreSkills.length || value.projectExperiences.length || value.workExperiences.length || value.internshipExperiences.length) }
async function submit() {
  if (!jobDescription.value.trim() || !salaryRange.value) { message.error('请填写岗位 JD 并选择薪资范围'); return }
  const setup: ComprehensiveSetup = { jobDescription: jobDescription.value.trim(), salaryRange: salaryRange.value, personalDescription: personalDescription.value.trim() || undefined, confirmedProfile: hasProfile(profile.value) ? profile.value : undefined, durationSeconds: durationSeconds.value, difficulty: difficulty.value }
  submitting.value = true
  try {
    if (existingId.value) await store.restore(existingId.value)
    const session = existingId.value ? await store.updateComprehensive(setup) : await store.createComprehensive(setup)
    await router.push(`/app/interviews/${session.interviewId}/ready`)
  } catch (error) { message.error(toApiError(error).message) } finally { submitting.value = false }
}
</script>
<template><section class="setup"><header><p class="eyebrow">COMPREHENSIVE INTERVIEW</p><h2>配置综合面试</h2><p>JD、简历和个人描述均按不可信输入处理；只有你确认后的结构化画像会进入面试上下文。</p></header><div class="panel"><label>岗位 JD（必填）<textarea v-model="jobDescription" rows="9" maxlength="30000" /></label><label>薪资范围（必填，税前月薪）<Select v-model:value="salaryRange" aria-label="薪资范围（必填）" placeholder="请选择薪资范围" :options="salaryOptions" /></label><label>补充个人描述（可选）<textarea v-model="personalDescription" rows="4" maxlength="5000" /></label><ResumeUploadField @parsed="profile=$event" /><h3>确认候选人画像</h3><ProfileDraftEditor v-model="profile" /><h3>面试设置</h3><InterviewSettingsPanel v-model:duration-seconds="durationSeconds" v-model:difficulty="difficulty" /><footer><Button @click="router.push('/app/interviews')">取消</Button><Button type="primary" size="large" :loading="submitting" @click="submit">{{ existingId ? '重新准备面试' : '确认并准备面试' }}</Button></footer></div></section></template>
<style scoped>.setup{display:grid;gap:24px;max-width:980px;margin:auto}.eyebrow{color:var(--cf-color-primary);font-weight:750}.setup h2{font-size:34px;margin:4px 0}.setup header p:last-child{color:var(--cf-color-text-muted)}.panel{display:grid;gap:20px;padding:28px;background:#fff;border:1px solid var(--cf-color-border);border-radius:18px;box-shadow:var(--cf-shadow-card)}.panel>label{display:grid;gap:8px;font-weight:650}.panel>label input,.panel>label textarea{padding:11px 13px;border:1px solid var(--cf-color-border);border-radius:9px;font:inherit}.panel :deep(.ant-select){width:100%}.panel footer{display:flex;justify-content:flex-end;gap:12px}</style>
