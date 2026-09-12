import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ request }) => {
  await ensureBaby(request)
  // Idempotence: clear growth records so the birth record is the only one.
  const records = await (await request.get('/api/growth-records')).json()
  for (const record of records) await request.delete(`/api/growth-records/${record.id}`)
})

test('records time of birth and birth measurements from the profile', async ({ page }) => {
  await page.goto('/profile')

  await page.getByLabel('出生时间').fill('14:30')
  await page.getByLabel(/^体重/).fill('3.25')
  await page.getByLabel(/^身长/).fill('49.5')
  await page.getByLabel(/^头围/).fill('34')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已保存')).toBeVisible()

  await page.reload()
  await expect(page.getByLabel('出生时间')).toHaveValue('14:30')
  await expect(page.getByLabel(/^体重/)).toHaveValue('3.25')

  // Time of birth is shown on the home summary.
  await page.goto('/')
  await expect(page.getByText('2026-01-15 14:30')).toBeVisible()

  // The measurements become the first growth point, labelled as the birth record.
  await page.goto('/growth')
  await expect(page.getByText('出生时')).toBeVisible()
  await expect(page.getByText('2026-01-15')).toBeVisible()
  await expect(page.getByText(/3\.25 公斤/)).toBeVisible()
})

test('clearing the birth measurements removes the birth record', async ({ page }) => {
  await page.goto('/profile')
  await page.getByLabel(/^体重/).fill('3.25')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已保存')).toBeVisible()

  await page.getByLabel(/^体重/).fill('')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已保存')).toBeVisible()

  await page.goto('/growth')
  await expect(page.getByText('还没有生长记录')).toBeVisible()
})
