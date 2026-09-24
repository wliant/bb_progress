import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  // Idempotence: clear today's entries.
  const logs = await (await request.get('/api/care-logs')).json()
  for (const log of logs) await request.delete(`/api/care-logs/${log.id}`)
  await page.goto('/care')
})

test('one-tap feeding log appears immediately with SGT time', async ({ page }) => {
  await page.getByRole('button', { name: /喂奶/ }).click()
  await expect(page.getByRole('status')).toContainText('已记录')

  // Entry appears in the day list with an HH:mm time.
  await expect(page.locator('li', { hasText: '喂奶' }).first()).toBeVisible()
  await expect(page.locator('li').first().getByText(/^\d{2}:\d{2}$/)).toBeVisible()

  // Count for feeding is 1.
  await expect(page.getByText(/🍼 1/)).toBeVisible()
})

/** The recent list on the home page is how a one-tap entry gets its detail. */
test('a quick log can be enriched from the home page', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: /喂奶/ }).click()
  await expect(page.getByRole('status')).toContainText('已记录')

  await expect(page.getByText('最近记录')).toBeVisible()
  await page.locator('li', { hasText: '喂奶' }).first().click()

  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await dialog.getByLabel(/备注/).fill('150ml')
  await dialog.getByRole('button', { name: '保存' }).click()

  await expect(dialog).toBeHidden()
  await expect(page.getByText(/150ml/)).toBeVisible()

  // The same entry shows the note on the care page.
  await page.goto('/care')
  await expect(page.getByText(/150ml/)).toBeVisible()
})

test('an entry can be deleted from its dialog', async ({ page }) => {
  await page.getByRole('button', { name: /换尿布/ }).click()
  await expect(page.getByRole('status')).toContainText('已记录')

  await page.locator('li', { hasText: '换尿布' }).first().click()
  await page.getByRole('dialog').getByRole('button', { name: '删除' }).click()

  await expect(page.getByText('这一天还没有记录')).toBeVisible()
})

/** A forgotten entry can be backfilled on a past day at the time it actually happened. */
test('quick log backfills a past day at the chosen time', async ({ page, request }) => {
  const yesterday = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Singapore' }).format(
    new Date(Date.now() - 86_400_000),
  )
  const logs = await (await request.get(`/api/care-logs?date=${yesterday}`)).json()
  for (const log of logs) await request.delete(`/api/care-logs/${log.id}`)

  await page.getByLabel('日期').fill(yesterday)
  await expect(page.getByText(`快速记录 · ${yesterday}`)).toBeVisible()
  await page.getByLabel('时间').fill('07:15')
  await page.getByRole('button', { name: '睡觉', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('已记录')

  const entry = page.locator('li', { hasText: '睡觉' }).first()
  await expect(entry.getByText('07:15')).toBeVisible()
  await expect(page.getByText(`${yesterday}: 🍼 0 · 😴 1 · 🧷 0`)).toBeVisible()

  // Back on today the time field goes away and the entry is not counted.
  await page.getByLabel('日期').fill(
    new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Singapore' }).format(new Date()),
  )
  await expect(page.getByText('快速记录', { exact: true })).toBeVisible()
  await expect(page.getByLabel('时间')).toHaveCount(0)
  await expect(page.getByText(/今日记录: 🍼 0 · 😴 0/)).toBeVisible()
})
