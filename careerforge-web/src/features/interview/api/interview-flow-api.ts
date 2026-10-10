import { httpClient } from '@/shared/api/http'
import { ApiError } from '@/shared/api/errors'

export type InterviewStage =
  | 'PREPARING' | 'READY' | 'INTERVIEWING' | 'GENERATING_REPORT' | 'COMPLETED' | 'FAILED'
export type InterviewMode = 'QUICK' | 'COMPREHENSIVE'
export type InterviewDifficulty = 'BASIC' | 'STANDARD' | 'CHALLENGE'
export interface InterviewSession { interviewId: string; mode: InterviewMode; stage: InterviewStage; version: number }
export interface CurrentQuestion { questionId: string; type: 'MAIN' | 'FOLLOW_UP'; displayOrder: number; content: string; draft: string; answerVersion: number; sessionVersion: number }
export interface TimerStatus { interviewId: string; timerState: string; remainingSeconds: number; version: number; leaseActive: boolean }
export interface SubmissionResult { interviewId: string; questionId: string; answerId: string; answerStatus: string; answerVersion: number; sessionStage: InterviewStage; timerState: string; sessionVersion: number; nextAction: string }
export interface InterviewRecord { interviewId: string; mode: InterviewMode; stage: InterviewStage; submittedAnswerCount: number; createdAt: string; version: number }
export interface InterviewPage { items: InterviewRecord[]; nextCursor: string | null }
export interface InterviewReport { reportId: string; interviewId: string; status: 'PENDING' | 'GENERATING' | 'READY' | 'FAILED'; reportJson?: string; failureCode?: string; version: number; updatedAt: string }
interface Envelope<T> { code: number; data?: T; message?: string }
export interface ProfileExperience { name: string; role: string; period: string; description: string; technologies: string[] }
export interface ConfirmedProfile { personalSummary: string; coreSkills: string[]; projectExperiences: ProfileExperience[]; workExperiences: ProfileExperience[]; internshipExperiences: ProfileExperience[] }
export interface ComprehensiveSetup {
  jobDescription: string
  salaryRange: string
  personalDescription?: string
  confirmedProfile?: ConfirmedProfile
  durationSeconds: 3600 | 5400
  difficulty: InterviewDifficulty
}

const freshRequestId = () => crypto.randomUUID()
const path = (id: string) => `/api/interviews/${encodeURIComponent(id)}`

export async function createQuickInterview() {
  return post<InterviewSession>('/api/interviews/quick', { requestId: freshRequestId(), direction: 'AI_AGENT_DEVELOPMENT' })
}
export async function parseResume(file: File) {
  const form = new FormData()
  form.append('file', file)
  return unwrap((await httpClient.post<Envelope<ConfirmedProfile>>(
    '/api/interviews/resume/parse', form,
  )).data)
}
export async function createComprehensiveInterview(setup: ComprehensiveSetup) {
  return post<InterviewSession>('/api/interviews/comprehensive', {
    requestId: freshRequestId(), direction: 'AI_AGENT_DEVELOPMENT', ...setup,
  })
}
export async function updateComprehensiveSetup(session: InterviewSession, setup: ComprehensiveSetup) {
  return put<InterviewSession>(`${path(session.interviewId)}/setup`, {
    ...setup, expectedVersion: session.version,
  })
}
export async function getInterview(id: string) { return get<InterviewSession>(path(id)) }
export async function getResumableInterview() { return get<{ interviewId: string; mode: InterviewMode; currentQuestionNumber: number; remainingSeconds: number } | null>('/api/interviews/resumable') }
export async function listInterviews(cursor?: string, limit = 20) { return get<InterviewPage>('/api/interviews', { limit, ...(cursor ? { cursor } : {}) }) }
export async function enterInterview(value: InterviewSession) { return post<InterviewSession>(`${path(value.interviewId)}/enter`, { requestId: freshRequestId(), expectedVersion: value.version }) }
export async function getCurrentQuestion(id: string) { return get<CurrentQuestion>(`${path(id)}/question`) }
export async function heartbeat(id: string) { return post<TimerStatus>(`${path(id)}/heartbeat`) }
export async function pauseInterview(id: string, expectedVersion: number) { return post<TimerStatus>(`${path(id)}/pause`, { expectedVersion }) }
export async function resumeInterview(id: string, expectedVersion: number) { return post<TimerStatus>(`${path(id)}/resume`, { expectedVersion }) }
export async function saveDraft(id: string, question: CurrentQuestion, content: string) { return put<{ version: number }>(`${path(id)}/questions/${question.questionId}/draft`, { content, expectedVersion: question.answerVersion < 0 ? 0 : question.answerVersion }) }
export async function submitAnswer(id: string, question: CurrentQuestion, content: string) { return post<SubmissionResult>(`${path(id)}/questions/${question.questionId}/submit`, { requestId: freshRequestId(), content, expectedVersion: question.answerVersion < 0 ? 0 : question.answerVersion }) }
export async function skipQuestion(id: string, question: CurrentQuestion) { return post<SubmissionResult>(`${path(id)}/questions/${question.questionId}/skip`, { requestId: freshRequestId(), expectedVersion: question.answerVersion < 0 ? 0 : question.answerVersion }) }
export async function finishInterview(value: InterviewSession) { return post<{ session: InterviewSession; reportGenerating: boolean }>(`${path(value.interviewId)}/finish`, { requestId: freshRequestId(), expectedVersion: value.version }) }
export async function getInterviewReport(id: string) { return get<InterviewReport>(`${path(id)}/report`) }
export async function retryInterviewReport(id: string) { return post<InterviewReport>(`${path(id)}/retry`) }
export async function getInterviewHistory(id: string) { return get<Array<{ question: { questionId: string; content: string; displayOrder: number }; answer?: { submittedContent?: string; status: string } }>>(`${path(id)}/history`) }

async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return unwrap((await httpClient.get<Envelope<T>>(url, { params })).data)
}
async function post<T>(url: string, body?: unknown): Promise<T> {
  return unwrap((await httpClient.post<Envelope<T>>(url, body)).data)
}
async function put<T>(url: string, body: unknown): Promise<T> {
  return unwrap((await httpClient.put<Envelope<T>>(url, body)).data)
}
function unwrap<T>(response: Envelope<T>): T {
  if (response.data === undefined) throw new ApiError({ kind: 'unknown', message: response.message || '服务端响应缺少数据' })
  return response.data
}
