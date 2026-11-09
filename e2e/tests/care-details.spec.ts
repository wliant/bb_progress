import { expect, test } from '@playwright/test'
import { ensureBaby } from './helpers'

test.beforeEach(async ({ page, request }) => {
  await ensureBaby(request)
  const logs = await (await request.get('/api/care-logs')).json()
  for (const log of logs) await request.delete(`/api/care-logs/${log.id}`)
  await page.goto('/care')
})

/** The requirement spec 08 exists for: a breastfeed carries minutes AND millilitres. */
test('a breastfeed records minutes and millilitres together', async ({ page }) => {
  await page.getByRole('button', { name: /喂奶/ }).click()
  await expect(page.getByRole('status')).toContainText('已记录')

  await page.locator('li').first().click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '母乳亲喂', exact: true }).click()
  await dialog.getByLabel(/亲喂时长/).fill('15')
  await dialog.getByLabel(/奶量/).fill('60')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  await expect(page.getByText('15 min · 60 ml')).toBeVisible()

  // The values survive a reload, so they were stored rather than held in the dialog.
  await page.reload()
  await expect(page.getByText('15 min · 60 ml')).toBeVisible()
})

test('a breastfeed with no amount says so instead of reading as zero', async ({ page }) => {
  await page.getByRole('button', { name: /喂奶/ }).click()
  await page.locator('li').first().click()

  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '母乳亲喂', exact: true }).click()
  await dialog.getByLabel(/亲喂时长/).fill('12')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  await expect(page.getByText(/12 min · 未记录奶量/)).toBeVisible()
})

test('a bottle feed has no minutes field, having no time at the breast', async ({ page }) => {
  await page.getByRole('button', { name: /喂奶/ }).click()
  await page.locator('li').first().click()

  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '瓶喂', exact: true }).click()
  await expect(dialog.getByLabel(/奶量/)).toBeVisible()
  await expect(dialog.getByLabel(/亲喂时长/)).toHaveCount(0)
})

test('a diaper change records one of the four conditions', async ({ page }) => {
  await page.getByRole('button', { name: /换尿布/ }).click()
  await page.locator('li').first().click()

  const dialog = page.getByRole('dialog')
  for (const name of ['干净', '稍脏', '脏', '很脏']) {
    await expect(dialog.getByRole('button', { name, exact: true })).toBeVisible()
  }
  await dialog.getByRole('button', { name: '很脏', exact: true }).click()
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  await expect(page.getByText('很脏')).toBeVisible()
})

test('a sleep is opened, shown as ongoing, then closed with one tap', async ({ page }) => {
  await page.getByRole('button', { name: '睡觉', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('已记录')
  await expect(page.getByText('仍在睡')).toBeVisible()

  await page.locator('li', { hasText: '仍在睡' }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: /现在醒了/ }).click()
  await expect(dialog).toBeHidden()

  // Closing it derives a duration, so the row no longer reads as ongoing.
  await expect(page.getByText(/睡了/)).toBeVisible()
  await expect(page.getByText('仍在睡')).toHaveCount(0)
})

test('the trends view totals the day and names its coverage', async ({ page, request }) => {
  // Seeded through the API with distinct times: two quick logs in the same minute sort
  // as a tie, and the dialog would reopen whichever row won, overwriting the first.
  // The UI path for entering detail is covered by the tests above; the subject here is
  // the trends view.
  // Minutes back from now, not fixed clock times: a future loggedAt is refused outright,
  // and the suite can run at any hour.
  const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString()
  const feeds = [
    { loggedAt: minutesAgo(3), method: 'BOTTLE', amountMl: 100 },
    { loggedAt: minutesAgo(2), method: 'BOTTLE', amountMl: 200 },
    // The unmeasured breastfeed: a real feed whose volume nobody wrote down.
    { loggedAt: minutesAgo(1), method: 'BREAST', durationMin: 20 },
  ]
  for (const feed of feeds) {
    const res = await request.post('/api/care-logs', { data: { type: 'FEEDING', ...feed } })
    expect(res.ok()).toBeTruthy()
  }

  await page.goto('/care')
  await page.getByRole('link', { name: '趋势' }).click()

  // 300 ml is real, but it came from 2 of 3 feeds, and the view never says the one
  // without the other.
  await expect(page.getByText('300 ml').first()).toBeVisible()
  await expect(page.getByText(/3 次喂养中的 2 次/)).toBeVisible()
  await expect(page.getByText('部分 — 有喂养未记录奶量')).toBeVisible()

  // The table view carries the same figures without relying on color.
  await page.getByRole('button', { name: /显示数据表/ }).click()
  await expect(page.getByRole('table')).toBeVisible()
  await expect(page.getByText('(2/3)')).toBeVisible()
})

test('a diaper change records wetness separately from dirtiness', async ({ page }) => {
  await page.getByRole('button', { name: /换尿布/ }).click()
  await page.locator('li').first().click()

  const dialog = page.getByRole('dialog')
  // Two independent facts: a change can be wet and clean, or dirty and dry.
  await dialog.getByRole('button', { name: '干净', exact: true }).click()
  await dialog.getByRole('button', { name: '尿湿', exact: true }).click()
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  await page.locator('li').first().click()
  const reopened = page.getByRole('dialog')
  await expect(reopened.getByRole('button', { name: '干净', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(reopened.getByRole('button', { name: '尿湿', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('reference ranges are shown, explained and can be switched off', async ({ page, request }) => {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Singapore' }).format(new Date())
  // A weight is what the intake band is derived from, and a chart with no data at all
  // renders its empty state rather than a legend, so there has to be a feed too.
  await request.post('/api/growth-records', { data: { measuredOn: today, weightKg: 4.7 } })
  await request.post('/api/care-logs', {
    data: {
      type: 'FEEDING',
      loggedAt: new Date(Date.now() - 60_000).toISOString(),
      method: 'BOTTLE',
      amountMl: 120,
    },
  })

  await page.goto('/care/trends')
  await expect(page.getByText('常见范围').first()).toBeVisible()
  // Reference information is never presented as advice for this baby.
  await expect(page.getByText(/并非针对您宝宝的建议/)).toBeVisible()

  const toggle = page.getByLabel('显示参考范围')
  await expect(toggle).toBeChecked()
  await toggle.uncheck()
  await expect(page.getByText('常见范围')).toHaveCount(0)
  await expect(page.getByText(/并非针对您宝宝的建议/)).toHaveCount(0)

  // The preference survives a reload.
  await page.reload()
  await expect(page.getByLabel('显示参考范围')).not.toBeChecked()
})

test('a day with an unmeasured feed is never called below the range', async ({ page, request }) => {
  const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString()
  await request.post('/api/growth-records', {
    data: { measuredOn: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Singapore' }).format(new Date()), weightKg: 4.7 },
  })
  // Far below the 564-846 ml band, but one feed was never measured.
  await request.post('/api/care-logs', {
    data: { type: 'FEEDING', loggedAt: minutesAgo(3), method: 'BOTTLE', amountMl: 100 },
  })
  await request.post('/api/care-logs', {
    data: { type: 'FEEDING', loggedAt: minutesAgo(2), method: 'BREAST', durationMin: 20 },
  })

  await page.goto('/care/trends')
  await expect(page.getByText('常见范围').first()).toBeVisible()
  // The verdict is withheld rather than inventing a shortfall out of a recording gap.
  await expect(page.getByText('低于常见范围')).toHaveCount(0)
})
