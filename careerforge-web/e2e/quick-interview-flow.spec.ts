import { expect, test } from '@playwright/test'

test('login recovery to quick interview report with REST fallback', async ({ page }) => {
  let stage: 'PREPARING'|'READY'|'INTERVIEWING'|'GENERATING_REPORT'|'COMPLETED'='PREPARING'
  const envelope=(data:unknown)=>({code:0,data,message:'ok'})
  await page.route('**/api/auth/refresh',route=>route.fulfill({json:envelope({userId:'u1',email:'u@example.com',displayName:'User',tokenType:'Bearer',accessToken:'token',accessTokenExpiresAt:'2099-01-01T00:00:00Z'})}))
  await page.route('**/api/interviews/resumable',route=>route.fulfill({json:envelope(null)}))
  await page.route('**/api/interviews/quick',async route=>{stage='PREPARING';await route.fulfill({json:envelope({interviewId:'i1',mode:'QUICK',stage,version:0})})})
  await page.route('**/api/interviews/i1',async route=>{if(stage==='PREPARING')stage='READY';await route.fulfill({json:envelope({interviewId:'i1',mode:'QUICK',stage,version:stage==='READY'?1:4})})})
  await page.route('**/api/interviews/i1/enter',async route=>{stage='INTERVIEWING';await route.fulfill({json:envelope({interviewId:'i1',mode:'QUICK',stage,version:2})})})
  await page.route('**/api/interviews/i1/question',route=>route.fulfill({json:envelope({questionId:'q1',type:'MAIN',displayOrder:10,content:'请完整说明如何设计一个可恢复的 Agent 任务流程。',draft:'',answerVersion:-1,sessionVersion:2})}))
  await page.route('**/api/interviews/i1/heartbeat',route=>route.fulfill({json:envelope({interviewId:'i1',timerState:'RUNNING',remainingSeconds:3600,version:2,leaseActive:true})}))
  await page.route('**/api/interviews/i1/questions/q1/draft',route=>route.fulfill({json:envelope({version:0})}))
  await page.route('**/api/interviews/i1/questions/q1/submit',async route=>{stage='GENERATING_REPORT';await route.fulfill({json:envelope({interviewId:'i1',questionId:'q1',answerId:'a1',answerStatus:'CLOSED',answerVersion:1,sessionStage:stage,timerState:'STOPPED',sessionVersion:3,nextAction:'GENERATING_REPORT'})})})
  await page.route('**/api/interviews/i1/report',async route=>{stage='COMPLETED';await route.fulfill({json:envelope({reportId:'r1',interviewId:'i1',status:'READY',version:2,updatedAt:new Date().toISOString(),reportJson:JSON.stringify({summary:'能够说明核心恢复路径',strengths:[{title:'可恢复设计',description:'有清晰的业务事实源',answerId:'a1',quote:'先保存业务事实'}],improvements:[],topicReviews:[{sequenceNo:1,conclusion:'已覆盖',answerIds:['a1']}]})})})})
  await page.route('**/api/interviews/i1/history',route=>route.fulfill({json:envelope([{question:{questionId:'q1',content:'请完整说明如何设计一个可恢复的 Agent 任务流程。',displayOrder:10},answer:{submittedContent:'先保存业务事实',status:'CLOSED'}}])}))
  await page.goto('/app/interviews');await expect(page.getByText('快速面试',{exact:true})).toBeVisible();await page.getByRole('button',{name:'开始准备'}).click();await expect(page.getByRole('button',{name:'正式开始'})).toBeEnabled();await page.getByRole('button',{name:'正式开始'}).click();
  await expect(page.getByText('请完整说明如何设计一个可恢复的 Agent 任务流程。')).toBeVisible();await page.locator('textarea').fill('先保存业务事实，再用有界重试和租约恢复。');await page.getByRole('button',{name:'提交回答'}).click();
  await expect(page.getByText('能够说明核心恢复路径')).toBeVisible();await page.reload();await expect(page.getByText('先保存业务事实',{exact:true})).toBeVisible()
})
