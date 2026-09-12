import { expect, test } from '@playwright/test'
import { ensureBaby, switchLanguage } from './helpers'

test.beforeEach(async ({ request }) => {
  await ensureBaby(request)
})

test('defaults to Chinese and can switch to English', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('宝宝成长记录').first()).toBeVisible()

  await switchLanguage(page, 'English')
  await expect(page.getByText('Baby Progress').first()).toBeVisible()

  await switchLanguage(page, '中文')
  await expect(page.getByText('宝宝成长记录').first()).toBeVisible()
})
