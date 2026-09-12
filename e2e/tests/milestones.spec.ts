import { expect, test } from '@playwright/test'
import { ensureBaby, switchLanguage } from './helpers'

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  // Idempotence: un-check the milestone this test uses.
  await request.delete('/api/milestones/2m-social-smiles/achievement')
  await page.goto('/milestones')
})

test('checks off a milestone with a date and it persists', async ({ page }) => {
  await page.getByRole('button', { name: /^2 个月/ }).click()
  await page.getByText('你对宝宝说话或微笑时会报以微笑').click()

  await page.getByLabel('达成日期').fill('2026-03-20')
  await page.getByRole('button', { name: '标记达成' }).click()

  await expect(page.getByText(/达成日期 2026-03-20/)).toBeVisible()

  await page.reload()
  await page.getByRole('button', { name: /^2 个月/ }).click()
  await expect(page.getByText(/达成日期 2026-03-20/)).toBeVisible()
})

test('milestone titles switch language instantly', async ({ page }) => {
  await page.getByRole('button', { name: /^2 个月/ }).click()
  await expect(page.getByText('趴着时能抬头')).toBeVisible()

  await switchLanguage(page, 'English')
  await expect(page.getByText('Holds head up when on tummy')).toBeVisible()
})
