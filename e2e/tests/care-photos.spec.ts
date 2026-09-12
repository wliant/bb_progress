import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'
import { noisyPng } from './images'

const ONE_MEGABYTE = 1_048_576

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  const logs = await (await request.get('/api/care-logs')).json()
  for (const log of logs) await request.delete(`/api/care-logs/${log.id}`)
  await page.goto('/care')
})

test('a phone-sized photo uploads and is stored under 1MB', async ({ page, request }) => {
  const photo = noisyPng(2400, 1800)
  expect(photo.length).toBeGreaterThan(3 * ONE_MEGABYTE)

  await page.getByRole('button', { name: /喂奶/ }).click()
  await expect(page.getByRole('status')).toContainText('已记录')
  await page.locator('li', { hasText: '喂奶' }).first().click()

  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: '添加照片' })).toBeVisible()
  await dialog.getByTestId('care-photo-input').setInputFiles({
    name: 'IMG_0001.png',
    mimeType: 'image/png',
    buffer: photo,
  });

  // The photo appears in the dialog without it being reopened.
  await expect(dialog.locator('img')).toBeVisible({ timeout: 20_000 })
  await expect(dialog.getByRole('button', { name: '更换照片' })).toBeVisible()

  // Stored copy is re-encoded to JPEG within the size budget.
  const logs = await (await request.get('/api/care-logs')).json()
  expect(logs[0].hasPhoto).toBe(true)
  const served = await request.get(`/api/care-logs/${logs[0].id}/photo`)
  expect(served.headers()['content-type']).toBe('image/jpeg')
  expect((await served.body()).length).toBeLessThanOrEqual(ONE_MEGABYTE)

  // And the list row shows a thumbnail.
  await page.keyboard.press('Escape')
  await expect(page.getByRole('img', { name: '照片' })).toBeVisible()
})

test('a photo can be removed from the entry', async ({ page, request }) => {
  await page.getByRole('button', { name: /睡觉/ }).click()
  await page.locator('li', { hasText: '睡觉' }).first().click()

  const dialog = page.getByRole('dialog')
  await dialog.getByTestId('care-photo-input').setInputFiles({
    name: 'small.png',
    mimeType: 'image/png',
    buffer: noisyPng(80, 60),
  })
  await expect(dialog.locator('img')).toBeVisible()

  await dialog.getByRole('button', { name: '删除照片' }).click()
  await expect(dialog.locator('img')).toHaveCount(0)

  const logs = await (await request.get('/api/care-logs')).json()
  expect(logs[0].hasPhoto).toBe(false)
})

test('the profile photo keeps its original bytes', async ({ page, request }) => {
  const original = noisyPng(900, 700)

  await page.goto('/profile')
  await page.getByTestId('photo-input').setInputFiles({
    name: 'me.png',
    mimeType: 'image/png',
    buffer: original,
  })
  await expect(page.locator('img').first()).toBeVisible()

  const served = await request.get('/api/baby/photo')
  expect(served.headers()['content-type']).toBe('image/png')
  expect((await served.body()).length).toBe(original.length)
})
