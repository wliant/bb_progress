import { expect, test, type APIRequestContext } from '@playwright/test'
import { ensureBaby } from './helpers'
import { noisyPng } from './images'

async function clearEntries(request: APIRequestContext) {
  const entries = await (await request.get('/api/head-shape')).json()
  for (const entry of entries) await request.delete(`/api/head-shape/${entry.id}`)
}

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  await clearEntries(request)
  await page.goto('/head-shape')
})

// These photos are gallery items, so leaving them behind would break the specs that
// assert an empty gallery.
test.afterEach(async ({ request }) => {
  await clearEntries(request)
})

test('photos for a day are added, shown and removed', async ({ page, request }) => {
  await expect(page.getByText('还没有头型照片')).toBeVisible()

  // Picking the day and the photos is the whole flow — the entry is created on the way.
  await page.getByTestId('head-shape-input').setInputFiles([
    { name: 'top.png', mimeType: 'image/png', buffer: noisyPng(1200, 900) },
    { name: 'back.png', mimeType: 'image/png', buffer: noisyPng(1200, 900) },
  ])

  await expect(page.getByText('2 张照片')).toBeVisible({ timeout: 20_000 })
  const tiles = page.locator('section img')
  await expect(tiles).toHaveCount(2)
  await expect(tiles.first()).toHaveAttribute('src', /size=thumb/)
  await expect(tiles.first()).toHaveAttribute('loading', 'lazy')

  const entries = await (await request.get('/api/head-shape')).json()
  expect(entries).toHaveLength(1)
  expect(entries[0].media).toHaveLength(2)

  // The viewer shows the full photo, not the thumbnail, and deletes just that one.
  await tiles.first().click()
  const viewer = page.getByRole('dialog')
  await expect(viewer.locator('img')).not.toHaveAttribute('src', /size=thumb/)
  await viewer.getByRole('button', { name: '移除' }).click()

  await expect(page.getByText('1 张照片')).toBeVisible()
  await expect(page.locator('section img')).toHaveCount(1)
})

test('the day keeps a note and the photos reach the media gallery', async ({ page }) => {
  await page.getByTestId('head-shape-input').setInputFiles({
    name: 'top.png',
    mimeType: 'image/png',
    buffer: noisyPng(400, 300),
  })
  await expect(page.getByText('1 张照片')).toBeVisible({ timeout: 20_000 })

  await page.getByRole('button', { name: /^编辑 / }).click()
  const dialog = page.getByRole('dialog')
  // A head shape entry takes photos only, so the recorder is not offered here.
  await expect(dialog.getByRole('button', { name: '录音' })).toHaveCount(0)
  await dialog.getByRole('textbox').fill('这一周一直朝右边睡')
  await dialog.getByRole('button', { name: '保存' }).click()

  await expect(page.getByText('这一周一直朝右边睡')).toBeVisible()

  await page.goto('/media')
  await expect(page.getByText('头型 · 这一周一直朝右边睡')).toBeVisible()
})

test('a video is refused with the documented error', async ({ page }) => {
  await page.getByTestId('head-shape-input').setInputFiles({
    name: 'top.png',
    mimeType: 'image/png',
    buffer: noisyPng(200, 150),
  })
  await expect(page.getByText('1 张照片')).toBeVisible({ timeout: 20_000 })

  await page.getByRole('button', { name: /^编辑 / }).click()
  const dialog = page.getByRole('dialog')
  // The picker filters by accept, but the server is what actually holds the rule.
  await expect(dialog.getByTestId('media-input')).toHaveAttribute(
    'accept',
    'image/jpeg,image/png,image/webp',
  )
  await dialog.getByTestId('media-input').setInputFiles({
    name: 'clip.mp4',
    mimeType: 'video/mp4',
    buffer: Buffer.from('fake-video-bytes'),
  })

  await expect(page.getByRole('alert')).toContainText('只支持照片')
  await expect(dialog.locator('img')).toHaveCount(1)
})
