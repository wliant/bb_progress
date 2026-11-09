import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { CareLogEditDialog } from './CareLogEditDialog'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'
import type { CareLog } from '../../api/types'

const base: CareLog = {
  id: 'c1',
  type: 'FEEDING',
  loggedAt: '2026-05-10T09:30:00+08:00',
  note: null,
  method: null,
  amountMl: null,
  durationMin: null,
  startedAt: null,
  endedAt: null,
  sleepMinutes: null,
  condition: null,
  media: [],
}

/** Captures the PUT body so each test can assert on what the dialog actually sends. */
function captureUpdate() {
  const sent: Record<string, unknown>[] = []
  server.use(
    http.get('/api/care-logs', () => HttpResponse.json([])),
    http.put('/api/care-logs/c1', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      sent.push(body)
      return HttpResponse.json({ ...base, ...body })
    }),
  )
  return sent
}

describe('care detail fields', () => {
  it('records minutes and ml together for a breastfeed', async () => {
    const sent = captureUpdate()
    renderApp(<CareLogEditDialog log={base} onClose={() => {}} />)

    await userEvent.click(screen.getByRole('button', { name: '母乳亲喂' }))
    await userEvent.type(screen.getByLabelText(/奶量/), '60')
    await userEvent.type(screen.getByLabelText(/亲喂时长/), '15')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0]).toMatchObject({ method: 'BREAST', amountMl: 60, durationMin: 15 })
  })

  it('hides the minutes field for a bottle, which has no time at the breast', async () => {
    renderApp(<CareLogEditDialog log={base} onClose={() => {}} />)
    await userEvent.click(screen.getByRole('button', { name: '瓶喂' }))
    expect(screen.queryByLabelText(/亲喂时长/)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/奶量/)).toBeInTheDocument()
  })

  it('sends a null amount when the field is left blank, never a zero', async () => {
    const sent = captureUpdate()
    renderApp(<CareLogEditDialog log={base} onClose={() => {}} />)

    await userEvent.click(screen.getByRole('button', { name: '母乳亲喂' }))
    await userEvent.type(screen.getByLabelText(/亲喂时长/), '12')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    // An unmeasured breastfeed must not be recorded as a feed of 0 ml.
    expect(sent[0].amountMl).toBeNull()
    expect(sent[0].durationMin).toBe(12)
  })

  it('carries only the fields the type owns, so the API cannot reject the write', async () => {
    const sent = captureUpdate()
    const diaper: CareLog = { ...base, type: 'DIAPER' }
    renderApp(<CareLogEditDialog log={diaper} onClose={() => {}} />)

    await userEvent.click(screen.getByRole('button', { name: '稍脏' }))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0]).toMatchObject({ condition: 'SLIGHTLY_DIRTY' })
    for (const foreign of ['method', 'amountMl', 'durationMin', 'startedAt', 'endedAt']) {
      expect(sent[0]).not.toHaveProperty(foreign)
    }
  })

  it('clears a condition when the active choice is tapped again', async () => {
    const sent = captureUpdate()
    const diaper: CareLog = { ...base, type: 'DIAPER', condition: 'DIRTY' }
    renderApp(<CareLogEditDialog log={diaper} onClose={() => {}} />)

    const dirty = screen.getByRole('button', { name: '脏' })
    expect(dirty).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(dirty)
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0].condition).toBeNull()
  })

  it('offers a one-tap close for an open sleep', async () => {
    const ended: string[] = []
    server.use(
      http.get('/api/care-logs', () => HttpResponse.json([])),
      http.post('/api/care-logs/c1/end-sleep', () => {
        ended.push('c1')
        return HttpResponse.json({ ...base, type: 'SLEEP', sleepMinutes: 95 })
      }),
    )
    const sleep: CareLog = {
      ...base,
      type: 'SLEEP',
      startedAt: '2026-05-10T09:30:00+08:00',
      endedAt: null,
    }
    renderApp(<CareLogEditDialog log={sleep} onClose={() => {}} />)

    await userEvent.click(screen.getByRole('button', { name: /现在醒了/ }))
    await waitFor(() => expect(ended).toEqual(['c1']))
  })

  it('has no end-sleep button once the sleep is closed', async () => {
    const sleep: CareLog = {
      ...base,
      type: 'SLEEP',
      startedAt: '2026-05-10T09:30:00+08:00',
      endedAt: '2026-05-10T11:05:00+08:00',
      sleepMinutes: 95,
    }
    renderApp(<CareLogEditDialog log={sleep} onClose={() => {}} />)
    expect(screen.queryByRole('button', { name: /现在醒了/ })).not.toBeInTheDocument()
    expect(screen.getByLabelText(/醒来/)).toHaveValue('2026-05-10T11:05')
  })
})
