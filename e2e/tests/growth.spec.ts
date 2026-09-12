import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  // Idempotence: clear existing growth records via the API.
  const records = await (await request.get('/api/growth-records')).json()
  for (const record of records) await request.delete(`/api/growth-records/${record.id}`)
  await page.goto('/growth')
})

test('adds a weight record and shows it in list and chart with WHO percentile curves', async ({
  page,
}) => {
  await page.getByRole('button', { name: '添加记录' }).click()
  await page.getByLabel('测量日期').fill('2026-03-15')
  await page.getByLabel(/^体重/).fill('5.4')
  await page.getByRole('button', { name: '保存' }).click()

  await expect(page.getByText('2026-03-15')).toBeVisible()
  await expect(page.getByText(/WHO/)).toBeVisible()

  // Percentile curves render as SVG paths inside the chart.
  const chart = page.getByTestId('growth-chart-WEIGHT')
  await expect(chart.locator('svg path.recharts-curve').first()).toBeVisible()
})

test('rejects a duplicate date with a localized error', async ({ page, request }) => {
  await request.post('/api/growth-records', {
    data: { measuredOn: '2026-03-20', weightKg: 5.6 },
  })
  await page.reload()
  await page.getByRole('button', { name: '添加记录' }).click()
  await page.getByLabel('测量日期').fill('2026-03-20')
  await page.getByLabel(/^体重/).fill('5.7')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('这一天已经有记录了')).toBeVisible()
})
