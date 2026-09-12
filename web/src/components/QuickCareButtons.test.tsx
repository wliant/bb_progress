import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QuickCareButtons } from './QuickCareButtons'
import { renderApp } from '../test/render'
import { apiError, server } from '../test/server'

describe('QuickCareButtons', () => {
  it('confirms a successful one-tap log', async () => {
    server.use(
      http.post('/api/care-logs', () =>
        HttpResponse.json({ id: 'x', type: 'FEEDING', loggedAt: '2026-05-10T09:30:00+08:00', note: null }, { status: 201 }),
      ),
    )
    renderApp(<QuickCareButtons />)
    await userEvent.click(screen.getByRole('button', { name: /喂奶/ }))
    expect(await screen.findByRole('status')).toHaveTextContent('已记录 喂奶')
  })

  // The primary daily action must never fail without telling the user.
  it('reports a failed log instead of doing nothing', async () => {
    server.use(http.post('/api/care-logs', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<QuickCareButtons />)
    await userEvent.click(screen.getByRole('button', { name: /喂奶/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('请先创建宝宝档案')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('reports a network failure in the user language', async () => {
    server.use(http.post('/api/care-logs', () => HttpResponse.error()))
    renderApp(<QuickCareButtons />, { language: 'en' })
    await userEvent.click(screen.getByRole('button', { name: /Feeding/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent("Can't reach the server")
  })
})
