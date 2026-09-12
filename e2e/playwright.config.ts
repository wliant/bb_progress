import { defineConfig, devices } from '@playwright/test'

// The docker compose stack must be running (docker compose up -d --build).
// Point E2E_BASE_URL at the instance's APP_PORT.
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: 1,
  workers: 1, // single shared backend state — run serially
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8090',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
  ],
})
