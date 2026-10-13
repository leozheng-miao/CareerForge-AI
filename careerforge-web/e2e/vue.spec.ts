import { expect, test } from '@playwright/test'

test('public home exposes the real product journey and public system pages', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle('水水面｜技术面试练习')
  await expect(page.getByRole('link', { name: '水水面首页' }).first()).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('面试前，先认真面一次。')
  await expect(page.getByText('60 分钟 · 8 个考查目标', { exact: true })).toBeVisible()
  await expect(page.getByText('结合岗位与真实经历', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('复盘具体回答，不给你一个虚构分数。')).toBeVisible()

  const modeEntries = page.locator('.home-mode-entry')
  await expect(modeEntries).toHaveCount(2)
  const quickBox = await modeEntries.nth(0).boundingBox()
  const comprehensiveBox = await modeEntries.nth(1).boundingBox()
  expect(quickBox).not.toBeNull()
  expect(comprehensiveBox).not.toBeNull()
  expect(Math.abs(quickBox!.y - comprehensiveBox!.y)).toBeLessThan(2)
  expect(comprehensiveBox!.x).toBeGreaterThan(quickBox!.x)

  await expect(page.getByRole('link', { name: '开始面试' }).first()).toHaveAttribute(
    'href',
    '/register',
  )
  await page.getByRole('link', { name: '用户协议' }).last().click()
  await expect(page).toHaveURL(/\/terms$/)
  await expect(page.getByRole('heading', { level: 1, name: '用户协议' })).toBeVisible()

  await page.goto('/privacy')
  await expect(page.getByRole('heading', { level: 1, name: '隐私政策' })).toBeVisible()

  await page.goto('/a-page-that-does-not-exist')
  await expect(page.getByRole('heading', { level: 1, name: '这个页面不存在' })).toBeVisible()
  await expect(page.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/')
})

test.describe('public home on a narrow viewport', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('stacks interview modes and exposes the mobile navigation without overflow', async ({
    page,
  }) => {
    await page.goto('/')

    const modeEntries = page.locator('.home-mode-entry')
    await expect(modeEntries).toHaveCount(2)
    const quickBox = await modeEntries.nth(0).boundingBox()
    const comprehensiveBox = await modeEntries.nth(1).boundingBox()
    expect(quickBox).not.toBeNull()
    expect(comprehensiveBox).not.toBeNull()
    expect(comprehensiveBox!.y).toBeGreaterThan(quickBox!.y + quickBox!.height - 2)

    const hasHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(hasHorizontalOverflow).toBe(false)

    await page.getByRole('button', { name: '切换导航菜单' }).click()
    await expect(page.getByRole('navigation', { name: '移动端导航' })).toBeVisible()
    await expect(
      page.getByRole('navigation', { name: '移动端导航' }).getByRole('link', {
        name: '开始面试',
      }),
    ).toHaveAttribute('href', '/register')
  })
})
