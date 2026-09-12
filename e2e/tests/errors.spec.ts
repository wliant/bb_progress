import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ request }) => {
  await ensureBaby(request)
})

test('a failed write is reported instead of failing silently', async ({ page }) => {
  await page.goto('/care')
  await page.route('**/api/care-logs', (route) =>
    route.request().method() === 'POST'
      ? route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ status: 500, code: 'INTERNAL_ERROR', message: 'x' }),
        })
      : route.continue(),
  )

  await page.getByRole('button', { name: /喂奶/ }).first().click()
  await expect(page.getByRole('alert')).toContainText('服务器出错了')
})

test('a failed read shows an error state, not an empty page', async ({ page }) => {
  await page.route('**/api/milestones', (route) =>
    route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ status: 500, code: 'INTERNAL_ERROR', message: 'x' }),
    }),
  )
  await page.goto('/milestones')
  await expect(page.getByRole('alert')).toContainText('加载失败')
  await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
})

test('moving the birth date past existing records is refused with an explanation', async ({
  page,
  request,
}) => {
  await request.post('/api/growth-records', { data: { measuredOn: '2026-02-20', weightKg: 4.9 } })

  await page.goto('/profile')
  await page.getByLabel('出生日期').fill('2026-06-01')
  await page.getByRole('button', { name: '保存' }).click()

  await expect(page.getByRole('alert')).toContainText('出生日期晚于已有的记录')

  // The stored profile is unchanged.
  await page.reload()
  await expect(page.getByLabel('出生日期')).toHaveValue('2026-01-15')
})
