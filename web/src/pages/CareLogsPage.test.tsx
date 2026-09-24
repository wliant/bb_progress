import { describe, expect, it } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { CareLogsPage } from './CareLogsPage'
import { renderApp } from '../test/render'
import { apiError, server } from '../test/server'

describe('CareLogsPage', () => {
  it('lists the day entries with SGT times', async () => {
    renderApp(<CareLogsPage />)
    expect(await screen.findByText('09:30')).toBeInTheDocument()
    expect(screen.getByText(/150ml/)).toBeInTheDocument()
  })

  it('logs now with no time field when today is selected', async () => {
    let body: unknown
    server.use(
      http.post('/api/care-logs', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ id: 'x', type: 'SLEEP', loggedAt: '2026-05-10T09:30:00+08:00', note: null }, { status: 201 })
      }),
    )
    renderApp(<CareLogsPage />)
    expect(await screen.findByText('快速记录')).toBeInTheDocument()
    expect(screen.queryByLabelText('时间')).not.toBeInTheDocument()
    expect(screen.getByText(/今日记录/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '睡觉' }))
    await screen.findByRole('status')
    expect(body).toEqual({ type: 'SLEEP' })
  })

  it('backfills a past day at the chosen SGT time', async () => {
    let body: unknown
    server.use(
      http.post('/api/care-logs', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ id: 'x', type: 'FEEDING', loggedAt: '2026-05-01T07:15:00+08:00', note: null }, { status: 201 })
      }),
    )
    renderApp(<CareLogsPage />, { route: '/care?date=2026-05-01', language: 'en' })
    expect(await screen.findByText('Quick log · 2026-05-01')).toBeInTheDocument()
    expect(screen.getByText(/^2026-05-01:/)).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Time'), { target: { value: '07:15' } })
    await userEvent.click(screen.getByRole('button', { name: 'Feeding' }))
    await screen.findByRole('status')
    expect(body).toEqual({ type: 'FEEDING', loggedAt: '2026-05-01T07:15:00+08:00' })
  })

  it('blocks a past-day log until a time is set', async () => {
    renderApp(<CareLogsPage />, { route: '/care?date=2026-05-01', language: 'en' })
    fireEvent.change(await screen.findByLabelText('Time'), { target: { value: '' } })
    expect(screen.getByRole('button', { name: 'Feeding' })).toBeDisabled()
  })

  it('prompts to create the profile when there is none', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<CareLogsPage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('shows an error state when the day fails to load', async () => {
    server.use(http.get('/api/care-logs', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<CareLogsPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
  })
})
