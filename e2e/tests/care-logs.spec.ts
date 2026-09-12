import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  // Idempotence: clear today's entries.
  const logs = await (await request.get('/api/care-logs')).json()
  for (const log of logs) await request.delete(`/api/care-logs/${log.id}`)
  await page.goto('/care')
})

test('one-tap feeding log appears immediately with SGT time', async ({ page }) => {
  await page.getByRole('button', { name: /喂奶/ }).click()
  await expect(page.getByRole('status')).toContainText('已记录')

  // Entry appears in the day list with an HH:mm time.
  await expect(page.locator('li', { hasText: '喂奶' }).first()).toBeVisible()
  await expect(page.locator('li').first().getByText(/^\d{2}:\d{2}$/)).toBeVisible()

  // Count for feeding is 1.
  await expect(page.getByText(/🍼 1/)).toBeVisible()
})
