import { expect, test } from '@playwright/test'

test('quick interview runs from the product center to a restorable formal report', async ({
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
  const submittedAnswer = '先保存业务事实，再用有界重试和活动租约恢复任务。'
  const envelope = (data: unknown) => ({ code: 0, data, message: 'ok' })

  await page.route('**/api/auth/refresh', (route) => route.fulfill({
    json: envelope({
      userId: 'quick-user',
      email: 'quick@example.com',
      displayName: '快速面试用户',
      tokenType: 'Bearer',
      accessToken: 'quick-test-token',
      accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    }),
  }))
  await page.route('**/api/interviews/resumable', (route) => (
    route.fulfill({ json: envelope(null) })
  ))
  await page.route(/\/api\/interviews(?:\?.*)?$/, (route) => route.fulfill({
    json: envelope({ items: [], nextCursor: null }),
  }))
  await page.route('**/api/interviews/quick', async (route) => {
    stage = 'PREPARING'
    version = 0
    preparationReads = 0
    await route.fulfill({
      json: envelope({ interviewId: 'quick-1', mode: 'QUICK', stage, version }),
    })
  })
  await page.route('**/api/interviews/quick-1/events', (route) => route.abort('failed'))
  await page.route('**/api/interviews/quick-1', async (route) => {
    if (stage === 'PREPARING') {
      preparationReads += 1
      if (preparationReads >= 2) {
        stage = 'READY'
        version = 1
      }
    }
    await route.fulfill({
      json: envelope({ interviewId: 'quick-1', mode: 'QUICK', stage, version }),
    })
  })
  await page.route('**/api/interviews/quick-1/enter', async (route) => {
    stage = 'INTERVIEWING'
    version = 2
    await route.fulfill({
      json: envelope({ interviewId: 'quick-1', mode: 'QUICK', stage, version }),
    })
  })
  await page.route('**/api/interviews/quick-1/question', (route) => route.fulfill({
    json: envelope({
      questionId: 'quick-question-1',
      type: 'MAIN',
      displayOrder: 10,
      content: '请完整说明如何设计一个可恢复的 Agent 任务流程。',
      draft: '',
      answerVersion: -1,
      sessionVersion: version,
    }),
  }))
  await page.route('**/api/interviews/quick-1/heartbeat', (route) => route.fulfill({
    json: envelope({
      interviewId: 'quick-1',
      timerState: 'RUNNING',
      remainingSeconds: 3600,
      version,
      leaseActive: true,
    }),
  }))
  await page.route('**/api/interviews/quick-1/questions/quick-question-1/draft', (route) => (
    route.fulfill({ json: envelope({ version: 0 }) })
  ))
  await page.route(
    '**/api/interviews/quick-1/questions/quick-question-1/submit',
    async (route) => {
      const request = route.request().postDataJSON() as { content: string }
      expect(request.content).toBe(submittedAnswer)
      stage = 'GENERATING_REPORT'
      version = 3
      await route.fulfill({
        json: envelope({
          interviewId: 'quick-1',
          questionId: 'quick-question-1',
          answerId: 'quick-answer-1',
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
  await page.route('**/api/interviews/quick-1/report', async (route) => {
    stage = 'COMPLETED'
    version = 4
    await route.fulfill({
      json: envelope({
        reportId: 'quick-report-1',
        interviewId: 'quick-1',
        status: 'READY',
        version: 2,
        updatedAt: '2026-10-13T08:00:00Z',
        reportJson: JSON.stringify({
          summary: '能够说明核心恢复路径。',
          strengths: [{
            title: '可恢复设计',
            description: '回答明确区分了业务事实与短期活动状态。',
            answerId: 'quick-answer-1',
            quote: '先保存业务事实',
          }],
          improvements: [],
          topicReviews: [{
            sequenceNo: 1,
            conclusion: '已经覆盖任务恢复的关键路径。',
            answerIds: ['quick-answer-1'],
          }],
        }),
      }),
    })
  })
  await page.route('**/api/interviews/quick-1/history', (route) => route.fulfill({
    json: envelope([{
      question: {
        questionId: 'quick-question-1',
        content: '请完整说明如何设计一个可恢复的 Agent 任务流程。',
        displayOrder: 10,
      },
      answer: { submittedContent: submittedAnswer, status: 'CLOSED' },
    }]),
  }))

  await page.goto('/app/interviews')
  await expect(page.getByRole('heading', { name: '准备开始哪一种面试？' })).toBeVisible()
  await expect(page.getByText('快速面试', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('综合面试', { exact: true }).first()).toBeVisible()

  await page.getByTestId('start-quick-interview').click()
  await expect(page.getByText('正在准备', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '正式开始' })).toBeEnabled()
  await expect(page.getByText('60 分钟', { exact: true })).toBeVisible()
  await expect(page.getByText('8 个考查目标', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '正式开始' }).click()
  await expect(page.locator('.app-sidebar')).toHaveCount(0)
  await expect(page.locator('.app-header')).toHaveCount(0)
  await expect(
    page.getByText('请完整说明如何设计一个可恢复的 Agent 任务流程。'),
  ).toBeVisible()

  await page.getByLabel('当前问题的回答').fill(submittedAnswer)
  await page.getByRole('button', { name: '提交回答' }).click()

  await expect(page.getByRole('heading', { name: '快速面试报告' })).toBeVisible()
  await expect(page.getByText('能够说明核心恢复路径。')).toBeVisible()
  await expect(page.getByRole('heading', { name: '完整问答记录' })).toBeVisible()
  await expect(page.locator('blockquote').filter({ hasText: submittedAnswer })).toBeVisible()
  await expect(page.getByText(/总分|百分制|排名|能力雷达/)).toHaveCount(0)

  await page.reload()
  await expect(page.getByRole('heading', { name: '快速面试报告' })).toBeVisible()
  await expect(page.locator('blockquote').filter({ hasText: submittedAnswer })).toBeVisible()
})
