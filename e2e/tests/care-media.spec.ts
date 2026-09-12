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
  await expect(dialog.getByRole('button', { name: '添加' })).toBeVisible()
  await dialog.getByTestId('media-input').setInputFiles({
    name: 'IMG_0001.png',
    mimeType: 'image/png',
    buffer: photo,
  });

  // The photo appears in the dialog without it being reopened.
  await expect(dialog.locator('img')).toBeVisible({ timeout: 20_000 })
  

  // Stored copy is re-encoded to JPEG within the size budget.
  const logs = await (await request.get('/api/care-logs')).json()
  expect(logs[0].media).toHaveLength(1)
  const served = await request.get(logs[0].media[0].url)
  expect(served.headers()['content-type']).toBe('image/jpeg')
  expect((await served.body()).length).toBeLessThanOrEqual(ONE_MEGABYTE)

  // And the list row shows a thumbnail.
  await page.keyboard.press('Escape')
  await expect(page.getByRole('img', { name: '照片' }).first()).toBeVisible()
})

test('a photo can be removed from the entry', async ({ page, request }) => {
  await page.getByRole('button', { name: /睡觉/ }).click()
  await page.locator('li', { hasText: '睡觉' }).first().click()

  const dialog = page.getByRole('dialog')
  await dialog.getByTestId('media-input').setInputFiles({
    name: 'small.png',
    mimeType: 'image/png',
    buffer: noisyPng(80, 60),
  })
  await expect(dialog.locator('img')).toBeVisible()

  await dialog.getByRole('button', { name: '移除' }).first().click()
  await expect(dialog.locator('img')).toHaveCount(0)

  const logs = await (await request.get('/api/care-logs')).json()
  expect(logs[0].media).toHaveLength(0)
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

/**
 * Drives the in-app recorder end to end. Only the browser's audio capture is stubbed —
 * headless Chromium on macOS cannot open an audio source at all (NotReadableError), even with
 * the fake-device flags. Everything after the Blob is real: the File, the upload, the stored
 * object and the playback element.
 */
test('a voice note recorded in the app is uploaded, stored and playable', async ({
  page,
  request,
}) => {
  await page.addInitScript(() => {
    const track = { stop: () => {} }
    const stream = { getTracks: () => [track] }
    // navigator.mediaDevices is a read-only accessor; plain assignment silently does nothing.
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: async () => stream },
    })

    class StubRecorder {
      static isTypeSupported = (type: string) => type === 'audio/webm;codecs=opus'
      mimeType = 'audio/webm;codecs=opus'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      onstop: (() => void) | null = null
      start() {}
      stop() {
        // ~40 KB of bytes so the upload is a genuine one.
        const bytes = new Uint8Array(40_000).map((_, i) => i % 251)
        this.ondataavailable?.({ data: new Blob([bytes], { type: 'audio/webm' }) })
        this.onstop?.()
      }
    }
    // @ts-expect-error replacing a browser API for the test
    window.MediaRecorder = StubRecorder
  })

  await page.goto('/care')
  await page.getByRole('button', { name: /睡觉/ }).click()
  await page.locator('li', { hasText: '睡觉' }).first().click()

  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: /录音/ }).click()
  await expect(dialog.getByRole('status')).toContainText('录音中')

  await dialog.getByRole('button', { name: '停止' }).click()
  // Wait for the attachment itself, not the picker's "照片 / 视频 / 录音" label.
  await expect(dialog.getByRole('button', { name: '移除' })).toHaveCount(1, { timeout: 20_000 })

  // Stored as audio, with the codecs parameter stripped from the content type.
  const logs = await (await request.get('/api/care-logs')).json()
  const voice = logs[0].media.find((m: { kind: string }) => m.kind === 'AUDIO')
  expect(voice).toBeTruthy()
  expect(voice.contentType).toBe('audio/webm')
  const served = await request.get(voice.url)
  expect(served.ok()).toBe(true)
  expect((await served.body()).length).toBe(40_000)

  // And it plays from the media page.
  await page.goto('/media')
  await page.getByText('录音').first().click()
  await expect(page.getByRole('dialog').locator('audio')).toHaveAttribute('controls', '')
})
