import type { APIRequestContext, Page } from '@playwright/test'

/** Ensures the single baby profile exists (idempotent via PUT). */
export async function ensureBaby(request: APIRequestContext) {
  const response = await request.put('/api/baby', {
    data: { name: '小宝', dateOfBirth: '2026-01-15', gender: 'FEMALE' },
  })
  if (!response.ok()) throw new Error(`Failed to ensure baby: ${response.status()}`)
}

/** Switches the UI language via the header/sidebar toggle. */
export async function switchLanguage(page: Page, lang: '中文' | 'English') {
  await page.getByRole('button', { name: lang }).first().click()
}
