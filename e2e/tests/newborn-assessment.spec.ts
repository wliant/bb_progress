import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ request }) => {
  await ensureBaby(request)
  const records = await (await request.get('/api/growth-records')).json()
  for (const record of records) await request.delete(`/api/growth-records/${record.id}`)
})

test('birth measurements are placed on the newborn standard once gestational age is known', async ({
  page,
}) => {
  await page.goto('/growth')
  // Without a gestational age there is nothing to compare against.
  await expect(page.getByTestId('newborn-assessment')).toHaveCount(0)

  await page.goto('/profile')
  await page.getByLabel('孕周（周）').fill('40')
  await page.getByLabel('孕周（天）').fill('0')
  await page.getByLabel(/^体重/).fill('3.38')
  await page.getByLabel(/^身长/).fill('49.9')
  await page.getByLabel(/^头围/).fill('34.3')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已保存')).toBeVisible()

  await page.goto('/growth')
  const card = page.getByTestId('newborn-assessment')
  await expect(card).toBeVisible()
  await expect(card.getByText('孕 40+0 周')).toBeVisible()
  // The published median for a boy at 40+0 — but this baby is a girl, so just assert a
  // plausible centile is shown for each of the three measurements.
  await expect(card.getByText(/第 \d+(\.\d+)? 百分位/)).toHaveCount(3)
  await expect(card.getByText(/INTERGROWTH-21st/)).toBeVisible()

  // Gestational age survives a reload.
  await page.goto('/profile')
  await expect(page.getByLabel('孕周（周）')).toHaveValue('40')
})

test('a very preterm gestation says the bundled standard does not cover it', async ({ page }) => {
  await page.goto('/profile')
  await page.getByLabel('孕周（周）').fill('30')
  await page.getByLabel('孕周（天）').fill('2')
  await page.getByLabel(/^体重/).fill('1.4')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已保存')).toBeVisible()

  await page.goto('/growth')
  const card = page.getByTestId('newborn-assessment')
  await expect(card.getByText(/不在本应用内置的标准范围内/)).toBeVisible()
  await expect(card.getByText(/百分位/)).toHaveCount(0)
})

test('the assessment matches the published median for a boy at 40+0 weeks', async ({
  page,
  request,
}) => {
  await request.put('/api/baby', {
    data: {
      name: '小宝',
      dateOfBirth: '2026-01-15',
      gender: 'MALE',
      gestationalAgeDays: 280,
      birthWeightKg: 3.38,
    },
  })

  await page.goto('/growth')
  const card = page.getByTestId('newborn-assessment')
  // 3.38 kg is the standard's published 50th centile for a boy at 40+0.
  await expect(card.getByText(/第 (49|50|51)(\.\d+)? 百分位/)).toBeVisible()
  await expect(card.getByText(/z [+-]?0\.0\d/)).toBeVisible()
})
