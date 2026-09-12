import { expect, test } from '@playwright/test'

test('creates/updates the profile and persists across reload', async ({ page }) => {
  await page.goto('/profile')

  await page.getByLabel('姓名').fill('测试宝宝')
  await page.getByLabel('出生日期').fill('2026-01-15')
  await page.getByText('女', { exact: true }).click()
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已保存')).toBeVisible()

  await page.reload()
  await expect(page.getByLabel('姓名')).toHaveValue('测试宝宝')

  // Home shows the baby summary with age.
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '测试宝宝' })).toBeVisible()
  await expect(page.getByText(/个月/).first()).toBeVisible()
})
