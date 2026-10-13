import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/features/interview/api/interview-flow-api'
import type { ComprehensiveSetup, CurrentQuestion, InterviewReport, InterviewSession, ProfileExperience, TimerStatus } from '@/features/interview/api/interview-flow-api'

export const useInterviewFlowStore = defineStore('interview-flow', () => {
  const session = ref<InterviewSession | null>(null)
  const question = ref<CurrentQuestion | null>(null)
  const timer = ref<TimerStatus | null>(null)
  const report = ref<InterviewReport | null>(null)
  const waiting = ref(false)
  const loading = ref(false)
  const comprehensiveSetup = ref<ComprehensiveSetup | null>(null)
  const currentId = computed(() => session.value?.interviewId ?? null)

  async function restore(interviewId?: string) {
    loading.value = true
    try {
      const id = interviewId ?? (await api.getResumableInterview())?.interviewId
      if (!id) { session.value = null; question.value = null; return null }
      session.value = await api.getInterview(id)
      question.value = null
      if (session.value.stage === 'INTERVIEWING') {
        try { question.value = await api.getCurrentQuestion(id) } catch { question.value = null }
        try { timer.value = await api.heartbeat(id) } catch { timer.value = null }
      }
      if (['GENERATING_REPORT', 'COMPLETED'].includes(session.value.stage)) {
        try { report.value = await api.getInterviewReport(id) } catch { report.value = null }
      }
      waiting.value = session.value.stage === 'INTERVIEWING' && !question.value
      return session.value
    } finally { loading.value = false }
  }

  async function createQuick() { session.value = await api.createQuickInterview(); return session.value }
  async function createComprehensive(setup: ComprehensiveSetup) {
    comprehensiveSetup.value = cloneComprehensiveSetup(setup)
    session.value = await api.createComprehensiveInterview(setup)
    return session.value
  }
  async function updateComprehensive(setup: ComprehensiveSetup) {
    if (!session.value || session.value.mode !== 'COMPREHENSIVE') throw new Error('综合面试会话不存在')
    comprehensiveSetup.value = cloneComprehensiveSetup(setup)
    session.value = await api.updateComprehensiveSetup(session.value, setup)
    return session.value
  }
  async function enter() {
    if (!session.value) throw new Error('面试会话不存在')
    session.value = await api.enterInterview(session.value)
    question.value = await api.getCurrentQuestion(session.value.interviewId)
    timer.value = await api.heartbeat(session.value.interviewId)
    return session.value
  }
  async function loadQuestionThenResume() {
    if (!session.value) return
    question.value = await api.getCurrentQuestion(session.value.interviewId)
    timer.value = await api.resumeInterview(session.value.interviewId, question.value.sessionVersion)
    session.value = { ...session.value, version: timer.value.version }
    waiting.value = false
  }
  async function saveDraft(content: string) {
    if (!session.value || !question.value) return
    const saved = await api.saveDraft(session.value.interviewId, question.value, content)
    question.value = { ...question.value, draft: content, answerVersion: saved.version }
  }
  async function submit(content: string) {
    if (!session.value || !question.value) return
    waiting.value = true
    const result = await api.submitAnswer(session.value.interviewId, question.value, content)
    session.value = { ...session.value, stage: result.sessionStage, version: result.sessionVersion }
    question.value = null
  }
  async function skip() {
    if (!session.value || !question.value) return
    waiting.value = true
    const result = await api.skipQuestion(session.value.interviewId, question.value)
    session.value = { ...session.value, stage: result.sessionStage, version: result.sessionVersion }
    question.value = null
  }
  async function pause() {
    if (!session.value) return
    timer.value = await api.pauseInterview(session.value.interviewId, session.value.version)
    session.value = { ...session.value, version: timer.value.version }
  }
  async function heartbeat() {
    if (!session.value || session.value.stage !== 'INTERVIEWING') return null
    timer.value = await api.heartbeat(session.value.interviewId)
    session.value = { ...session.value, version: timer.value.version }
    return timer.value
  }
  async function pauseActiveBeforeLogout() {
    let activeSession = session.value
    if (!activeSession) {
      const resumable = await api.getResumableInterview()
      if (!resumable) return
      activeSession = await api.getInterview(resumable.interviewId)
      session.value = activeSession
    }
    if (activeSession.stage !== 'INTERVIEWING') return
    timer.value = await api.pauseInterview(activeSession.interviewId, activeSession.version)
    session.value = { ...activeSession, version: timer.value.version }
  }
  function pauseForPageExit() {
    if (!session.value || session.value.stage !== 'INTERVIEWING') return
    if (timer.value && timer.value.timerState !== 'RUNNING') return
    api.pauseInterviewOnPageExit(session.value.interviewId, session.value.version)
  }
  async function resume() { await loadQuestionThenResume() }
  async function finish() {
    if (!session.value) return
    const result = await api.finishInterview(session.value)
    session.value = result.session
    question.value = null
  }

  return { session, question, timer, report, waiting, loading, currentId, comprehensiveSetup,
    restore, createQuick, createComprehensive, updateComprehensive, enter, loadQuestionThenResume, saveDraft, submit, skip,
    heartbeat, pause, pauseActiveBeforeLogout, pauseForPageExit, resume, finish }
})

function cloneComprehensiveSetup(setup: ComprehensiveSetup): ComprehensiveSetup {
  const profile = setup.confirmedProfile
  return {
    ...setup,
    confirmedProfile: profile
      ? {
          personalSummary: profile.personalSummary,
          coreSkills: [...profile.coreSkills],
          projectExperiences: profile.projectExperiences.map(cloneExperience),
          workExperiences: profile.workExperiences.map(cloneExperience),
          internshipExperiences: profile.internshipExperiences.map(cloneExperience),
        }
      : undefined,
  }
}

function cloneExperience(experience: ProfileExperience): ProfileExperience {
  return { ...experience, technologies: [...experience.technologies] }
}
