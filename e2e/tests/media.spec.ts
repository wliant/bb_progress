import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'
import { noisyPng } from './images'

test.beforeEach(async ({ request }) => {
  await ensureBaby(request)
  const logs = await (await request.get('/api/care-logs')).json()
  for (const log of logs) await request.delete(`/api/care-logs/${log.id}`)
  await request.delete('/api/milestones/2m-social-smiles/achievement')
  await request.delete('/api/milestones/2m-motor-head-up/achievement')
})

test('media added to a care entry shows up in the gallery', async ({ page }) => {
  await page.goto('/media')
  await expect(page.getByText('还没有任何内容')).toBeVisible()

  // Log a feed, add a photo and a note to it.
  await page.goto('/care')
  await page.getByRole('button', { name: /喂奶/ }).click()
  await page.locator('li', { hasText: '喂奶' }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/备注/).fill('午餐')
  await dialog.getByTestId('media-input').setInputFiles({
    name: 'lunch.png',
    mimeType: 'image/png',
    buffer: noisyPng(900, 700),
  })
  await expect(dialog.locator('img')).toBeVisible()
  await dialog.getByRole('button', { name: '保存' }).click()

  await page.goto('/media')
  await expect(page.getByText('共 1 项')).toBeVisible()
  await expect(page.getByText('喂奶 · 午餐')).toBeVisible()

  // The tile must use the thumbnail, not the full image.
  const tile = page.getByRole('img').first()
  await expect(tile).toHaveAttribute('src', /size=thumb/)
  await expect(tile).toHaveAttribute('loading', 'lazy')
})

test('the viewer shows the full image and links back to the entry', async ({ page }) => {
  await page.goto('/care')
  await page.getByRole('button', { name: /换尿布/ }).click()
  await page.locator('li', { hasText: '换尿布' }).first().click()
  await page.getByRole('dialog').getByTestId('media-input').setInputFiles({
    name: 'p.png',
    mimeType: 'image/png',
    buffer: noisyPng(600, 450),
  })
  await expect(page.getByRole('dialog').locator('img')).toBeVisible()
  await page.keyboard.press('Escape')

  await page.goto('/media')
  await page.getByRole('img').first().click()

  const viewer = page.getByRole('dialog')
  await expect(viewer).toBeVisible()
  await expect(viewer.getByRole('img')).toHaveAttribute('src', /^(?!.*size=thumb).*$/)
  await viewer.getByRole('link', { name: '查看原记录' }).click()
  await expect(page).toHaveURL(/\/care\?date=/)
  await expect(page.locator('li', { hasText: '换尿布' }).first()).toBeVisible()
})

test('a milestone photo appears with its title and follows the language switcher', async ({
  page,
}) => {
  await page.goto('/milestones')
  await page.getByRole('button', { name: /^2 个月/ }).click()
  const row = page.locator('li', { hasText: '你对宝宝说话或微笑时会报以微笑' })
  await row.click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('达成日期').fill('2026-03-20')
  await dialog.getByRole('button', { name: '标记达成' }).click()
  await expect(dialog).toBeHidden()

  await row.click()
  await page.getByRole('dialog').getByTestId('media-input').setInputFiles({
    name: 'smile.png',
    mimeType: 'image/png',
    buffer: noisyPng(500, 400),
  })
  await expect(page.getByRole('dialog').locator('img')).toBeVisible()
  await page.keyboard.press('Escape')

  await page.goto('/media')
  await expect(page.getByText('2026-03-20')).toBeVisible()
  await expect(page.getByText('你对宝宝说话或微笑时会报以微笑').first()).toBeVisible()

  await page.getByRole('button', { name: 'English' }).filter({ visible: true }).first().click()
  await expect(page.getByText('Smiles when you talk to or smile at them').first()).toBeVisible()
})

/** Attaching a photo to a milestone must not require saving, reopening, then uploading. */
test('milestone media can be added in the same dialog as marking it achieved', async ({
  page,
  request,
}) => {
  await page.goto('/milestones')
  await page.getByRole('button', { name: /^2 个月/ }).click()
  await page.locator('li', { hasText: '趴着时能抬头' }).click()

  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: '添加' })).toBeVisible()
  await dialog.getByLabel('达成日期').fill('2026-04-02')
  await dialog.getByTestId('media-input').setInputFiles({
    name: 'tummy.png',
    mimeType: 'image/png',
    buffer: noisyPng(700, 520),
  })
  // Held as a local preview until the achievement exists.
  await expect(dialog.locator('img')).toBeVisible()
  await expect(dialog.getByText(/会在保存后一起上传/)).toBeVisible()

  await dialog.getByRole('button', { name: '标记达成' }).click()
  await expect(dialog).toBeHidden()

  const milestones = await (await request.get('/api/milestones')).json()
  const achieved = milestones
    .flatMap((g: { milestones: unknown[] }) => g.milestones)
    .find((m: { id: string }) => m.id === '2m-motor-head-up')
  expect(achieved.achievement.achievedOn).toBe('2026-04-02')
  expect(achieved.achievement.media).toHaveLength(1)

  await page.goto('/media')
  await expect(page.getByText('2026-04-02')).toBeVisible()
  await expect(page.getByText('趴着时能抬头').first()).toBeVisible()
})

test('removing an attachment removes it from the gallery', async ({ page }) => {
  await page.goto('/care')
  await page.getByRole('button', { name: /睡觉/ }).click()
  await page.locator('li', { hasText: '睡觉' }).first().click()
  await page.getByRole('dialog').getByTestId('media-input').setInputFiles({
    name: 'x.png',
    mimeType: 'image/png',
    buffer: noisyPng(400, 300),
  })
  await expect(page.getByRole('dialog').locator('img')).toBeVisible()

  await page.goto('/media')
  await expect(page.getByText('共 1 项')).toBeVisible()

  await page.goto('/care')
  await page.locator('li', { hasText: '睡觉' }).first().click()
  await page.getByRole('dialog').getByRole('button', { name: '移除' }).first().click()
  await expect(page.getByRole('dialog').locator('img')).toHaveCount(0)

  await page.goto('/media')
  await expect(page.getByText('还没有任何内容')).toBeVisible()
})
