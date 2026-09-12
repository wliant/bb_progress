import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

/**
 * Runs against a wiped app: every feature page must offer to create the profile
 * rather than showing controls that would fail on use. Restores the profile at the
 * end so the remaining specs run against a normal app.
 */
test.describe.serial('first run', () => {
  test.beforeAll(async ({ request }) => {
    await request.delete('/api/baby')
  })

  test.afterAll(async ({ request }) => {
    await ensureBaby(request)
  })

  for (const path of ['/', '/growth', '/milestones', '/care']) {
    test(`${path} prompts to create the profile`, async ({ page }) => {
      await page.goto(path)
      await expect(page.getByText('还没有宝宝档案，先创建一个吧！')).toBeVisible()
      await expect(page.getByRole('link', { name: '创建档案' })).toBeVisible()
    })
  }

  test('milestones checklist is not reachable before a profile exists', async ({ page }) => {
    await page.goto('/milestones')
    await expect(page.getByRole('button', { name: /^2 个月/ })).toHaveCount(0)
  })
})
