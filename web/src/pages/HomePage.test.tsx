import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { HomePage } from './HomePage'
import { renderApp } from '../test/render'
import { apiError, server, testCareLogs } from '../test/server'

describe('HomePage', () => {
  it('shows the baby summary with name and birth date/time', async () => {
    renderApp(<HomePage />)
    expect(await screen.findByText('小测试')).toBeInTheDocument()
    expect(screen.getByText('2026-01-15 14:30')).toBeInTheDocument()
    expect(screen.getByText('快速记录')).toBeInTheDocument()
  })

  it('prompts to create a profile when none exists', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<HomePage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('renders in English when the language is switched', async () => {
    renderApp(<HomePage />, { language: 'en' })
    expect(await screen.findByText('小测试')).toBeInTheDocument()
    expect(screen.getByText('Quick log')).toBeInTheDocument()
  })

  it('lists recent care logs with their SGT times', async () => {
    renderApp(<HomePage />)
    expect(await screen.findByText('最近记录')).toBeInTheDocument()
    expect(await screen.findByText('09:30')).toBeInTheDocument()
    expect(screen.getByText(/150ml/)).toBeInTheDocument()
  })

  // The point of the recent list: one-tap logs get their detail added afterwards.
  it('opens the edit dialog from a recent entry and saves a note', async () => {
    const sent = vi.fn()
    server.use(
      http.put('/api/care-logs/:id', async ({ request }) => {
        const body = await request.json()
        sent(body)
        return HttpResponse.json({ ...testCareLogs[0], ...(body as object) })
      }),
    )
    renderApp(<HomePage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('喂奶')

    const noteInput = screen.getByLabelText(/备注/)
    await userEvent.clear(noteInput)
    await userEvent.type(noteInput, '180ml')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveBeenCalled())
    expect(sent.mock.calls[0][0]).toMatchObject({ note: '180ml' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('shows an empty-state when there are no logs today', async () => {
    server.use(http.get('/api/care-logs', () => HttpResponse.json([])))
    renderApp(<HomePage />)
    expect(await screen.findByText('这一天还没有记录')).toBeInTheDocument()
  })
})
