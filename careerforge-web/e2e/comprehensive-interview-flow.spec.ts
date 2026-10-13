import { expect, test } from '@playwright/test'

test('comprehensive interview preserves confirmed inputs through setup updates and reporting', async ({
  page,
}) => {
  type Stage =
    | 'PREPARING'
    | 'READY'
    | 'INTERVIEWING'
    | 'GENERATING_REPORT'
    | 'COMPLETED'

  let stage: Stage = 'PREPARING'
  let version = 0
  let preparationReads = 0
  let createPayload: Record<string, unknown> | null = null
  let updatePayload: Record<string, unknown> | null = null
  const submittedAnswer = 'MySQL 保存业务事实，Redis 只承担短期租约，并用幂等键恢复任务。'
  const envelope = (data: unknown) => ({ code: 0, data, message: 'ok' })

  await page.route('**/api/auth/refresh', (route) => route.fulfill({
    json: envelope({
      userId: 'comprehensive-user',
      email: 'comprehensive@example.com',
      displayName: '综合面试用户',
      tokenType: 'Bearer',
      accessToken: 'comprehensive-test-token',
      accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    }),
  }))
  await page.route('**/api/interviews/resumable', (route) => (
    route.fulfill({ json: envelope(null) })
  ))
  await page.route('**/api/interviews/resume/parse', (route) => route.fulfill({
    json: envelope({
      personalSummary: 'Java Agent 开发者',
      coreSkills: ['Java', 'RAG'],
      projectExperiences: [{
        name: 'Agent 平台',
        role: '后端开发',
        period: '2026',
        description: '实现任务恢复',
        technologies: ['Java'],
      }],
      workExperiences: [],
      internshipExperiences: [],
    }),
  }))
  await page.route('**/api/interviews/comprehensive', async (route) => {
    createPayload = route.request().postDataJSON() as Record<string, unknown>
    stage = 'PREPARING'
    version = 0
    preparationReads = 0
    await route.fulfill({
      json: envelope({
        interviewId: 'comprehensive-1',
        mode: 'COMPREHENSIVE',
        stage,
        version,
      }),
    })
  })
  await page.route('**/api/interviews/comprehensive-1/setup', async (route) => {
    updatePayload = route.request().postDataJSON() as Record<string, unknown>
    stage = 'PREPARING'
    version += 1
    preparationReads = 0
    await route.fulfill({
      json: envelope({
        interviewId: 'comprehensive-1',
        mode: 'COMPREHENSIVE',
        stage,
        version,
      }),
    })
  })
  await page.route('**/api/interviews/comprehensive-1/events', (route) => route.abort('failed'))
  await page.route('**/api/interviews/comprehensive-1', async (route) => {
    if (stage === 'PREPARING') {
      preparationReads += 1
      if (preparationReads >= 2) {
        stage = 'READY'
        version += 1
      }
    }
    await route.fulfill({
      json: envelope({
        interviewId: 'comprehensive-1',
        mode: 'COMPREHENSIVE',
        stage,
        version,
      }),
    })
  })
  await page.route('**/api/interviews/comprehensive-1/enter', async (route) => {
    stage = 'INTERVIEWING'
    version += 1
    await route.fulfill({
      json: envelope({
        interviewId: 'comprehensive-1',
        mode: 'COMPREHENSIVE',
        stage,
        version,
      }),
    })
  })
  await page.route('**/api/interviews/comprehensive-1/question', (route) => route.fulfill({
    json: envelope({
      questionId: 'comprehensive-question-1',
      type: 'MAIN',
      displayOrder: 10,
      content: '请说明你如何保证 Agent 任务可恢复。',
      draft: '',
      answerVersion: -1,
      sessionVersion: version,
    }),
  }))
  await page.route('**/api/interviews/comprehensive-1/heartbeat', (route) => route.fulfill({
    json: envelope({
      interviewId: 'comprehensive-1',
      timerState: 'RUNNING',
      remainingSeconds: 5400,
      version,
      leaseActive: true,
    }),
  }))
  await page.route(
    '**/api/interviews/comprehensive-1/questions/comprehensive-question-1/draft',
    (route) => route.fulfill({ json: envelope({ version: 0 }) }),
  )
  await page.route(
    '**/api/interviews/comprehensive-1/questions/comprehensive-question-1/submit',
    async (route) => {
      const request = route.request().postDataJSON() as { content: string }
      expect(request.content).toBe(submittedAnswer)
      stage = 'GENERATING_REPORT'
      version += 1
      await route.fulfill({
        json: envelope({
          interviewId: 'comprehensive-1',
          questionId: 'comprehensive-question-1',
          answerId: 'comprehensive-answer-1',
          answerStatus: 'CLOSED',
          answerVersion: 1,
          sessionStage: stage,
          timerState: 'STOPPED',
          sessionVersion: version,
          nextAction: 'GENERATING_REPORT',
        }),
      })
    },
  )
  await page.route('**/api/interviews/comprehensive-1/report', async (route) => {
    stage = 'COMPLETED'
    version += 1
    await route.fulfill({
      json: envelope({
        reportId: 'comprehensive-report-1',
        interviewId: 'comprehensive-1',
        status: 'READY',
        version: 1,
        updatedAt: '2026-10-13T08:00:00Z',
        reportJson: JSON.stringify({
          summary: '回答展示了可恢复设计。',
          strengths: [{
            title: '业务事实明确',
            description: '回答说明了持久化边界。',
            answerId: 'comprehensive-answer-1',
            quote: 'MySQL 保存业务事实',
          }],
          improvements: [],
          topicReviews: [{
            sequenceNo: 1,
            conclusion: '已经覆盖状态恢复主题。',
            answerIds: ['comprehensive-answer-1'],
          }],
        }),
      }),
    })
  })
  await page.route('**/api/interviews/comprehensive-1/history', (route) => route.fulfill({
    json: envelope([{
      question: {
        questionId: 'comprehensive-question-1',
        content: '请说明你如何保证 Agent 任务可恢复。',
        displayOrder: 10,
      },
      answer: { submittedContent: submittedAnswer, status: 'CLOSED' },
    }]),
  }))

  await page.goto('/app/interviews/comprehensive/setup')
  await expect(page.getByRole('heading', { name: '岗位信息' })).toBeVisible()
  await page.locator('textarea[name="jobDescription"]').fill(
    '招聘 Java Agent 工程师，要求 RAG、可靠性和评测经验。',
  )
  await page.getByLabel('薪资范围（必填）').click()
  await page.getByRole('option', { name: '30K-40K/月' }).click()
  await page.locator('textarea[name="personalDescription"]').fill(
    '重点验证状态恢复和工具调用边界。',
  )
  await page.getByTestId('next-step').click()

  await expect(
    page.getByRole('heading', { name: '个人材料与候选人画像' }),
  ).toBeVisible()
  await page.locator('input[type=file]').setInputFiles({
    name: 'resume.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4 controlled fixture'),
  })
  await expect(page.getByLabel('个人概述')).toHaveValue('Java Agent 开发者')
  await page.getByLabel('个人概述').fill('我确认过的 Java Agent 项目经历')
  await page.getByTestId('next-profile-step').click()

  await expect(page.getByRole('heading', { name: '面试设置与最后确认' })).toBeVisible()
  await page.getByRole('radio', { name: /90 分钟/ }).check()
  await page.getByRole('radio', { name: /挑战/ }).check()
  await expect(page.locator('.setup-summary')).toContainText('90 分钟')
  await expect(page.locator('.setup-summary')).toContainText('挑战')
  await expect(page.locator('.setup-summary')).toContainText('30K-40K/月')
  await page.getByTestId('submit-setup').click()

  await expect(page.getByText('正在准备', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '正式开始' })).toBeEnabled()
  await expect(page.locator('.ready-card__facts')).toContainText('90 分钟')
  await expect(page.locator('.ready-card__facts')).toContainText('挑战')

  await page.getByRole('button', { name: '返回修改设置' }).click()
  await expect(page.getByRole('heading', { name: '修改面试设置' })).toBeVisible()
  await expect(page.locator('textarea[name="jobDescription"]')).toHaveValue(
    '招聘 Java Agent 工程师，要求 RAG、可靠性和评测经验。',
  )
  await page.getByLabel('薪资范围（必填）').click()
  await page.getByRole('option', { name: '40K-60K/月' }).click()
  await page.getByTestId('next-step').click()
  await expect(page.getByLabel('个人概述')).toHaveValue('我确认过的 Java Agent 项目经历')
  await page.getByTestId('next-profile-step').click()
  await expect(page.getByRole('radio', { name: /90 分钟/ })).toBeChecked()
  await expect(page.getByRole('radio', { name: /挑战/ })).toBeChecked()
  await page.getByTestId('submit-setup').click()

  await expect(page.getByRole('button', { name: '正式开始' })).toBeEnabled()
  await page.getByRole('button', { name: '正式开始' }).click()
  await expect(page.locator('.app-sidebar')).toHaveCount(0)
  await expect(page.locator('.app-header')).toHaveCount(0)
  await expect(page.getByText('请说明你如何保证 Agent 任务可恢复。')).toBeVisible()

  await page.getByLabel('当前问题的回答').fill(submittedAnswer)
  await page.getByRole('button', { name: '提交回答' }).click()

  await expect(page.getByRole('heading', { name: '综合面试报告' })).toBeVisible()
  await expect(page.getByText('回答展示了可恢复设计。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '考查回顾' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '主题 1' })).toBeVisible()
  await expect(page.locator('blockquote').filter({ hasText: submittedAnswer })).toBeVisible()
  await expect(page.getByText(/总分|百分制|排名|能力雷达/)).toHaveCount(0)

  expect(createPayload).toMatchObject({
    jobDescription: '招聘 Java Agent 工程师，要求 RAG、可靠性和评测经验。',
    salaryRange: '30K-40K/月',
    personalDescription: '重点验证状态恢复和工具调用边界。',
    durationSeconds: 5400,
    difficulty: 'CHALLENGE',
    confirmedProfile: {
      personalSummary: '我确认过的 Java Agent 项目经历',
      coreSkills: ['Java', 'RAG'],
    },
  })
  expect(updatePayload).toMatchObject({
    salaryRange: '40K-60K/月',
    durationSeconds: 5400,
    difficulty: 'CHALLENGE',
    expectedVersion: 1,
  })
})
