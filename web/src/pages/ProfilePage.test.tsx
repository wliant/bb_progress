import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { ProfilePage } from './ProfilePage'
import { renderApp } from '../test/render'
import { apiError, server, testBaby } from '../test/server'

describe('ProfilePage', () => {
  it('loads birth details into the form', async () => {
    renderApp(<ProfilePage />)
    expect(await screen.findByLabelText('姓名')).toHaveValue('小测试')
    expect(screen.getByLabelText('出生日期')).toHaveValue('2026-01-15')
    // The API sends "14:30:00"; the time input needs HH:mm.
    expect(screen.getByLabelText('出生时间')).toHaveValue('14:30')
    expect(screen.getByLabelText(/体重/)).toHaveValue(3.25)
    expect(screen.getByLabelText(/身长/)).toHaveValue(49.5)
    expect(screen.getByLabelText(/头围/)).toHaveValue(34)
  })

  it('submits time of birth and birth measurements', async () => {
    const sent = vi.fn()
    server.use(
      http.put('/api/baby', async ({ request }) => {
        const body = await request.json()
        sent(body)
        return HttpResponse.json({ ...testBaby, ...(body as object) })
      }),
    )
    renderApp(<ProfilePage />)
    await screen.findByLabelText('姓名')

    await userEvent.clear(screen.getByLabelText('出生时间'))
    await userEvent.type(screen.getByLabelText('出生时间'), '09:05')
    await userEvent.clear(screen.getByLabelText(/体重/))
    await userEvent.type(screen.getByLabelText(/体重/), '3.4')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveBeenCalled())
    expect(sent.mock.calls[0][0]).toMatchObject({
      timeOfBirth: '09:05',
      birthWeightKg: 3.4,
      birthLengthCm: 49.5,
      birthHeadCircumferenceCm: 34,
    })
  })

  it('sends nulls when birth details are cleared', async () => {
    const sent = vi.fn()
    server.use(
      http.put('/api/baby', async ({ request }) => {
        const body = await request.json()
        sent(body)
        return HttpResponse.json({ ...testBaby, ...(body as object) })
      }),
    )
    renderApp(<ProfilePage />)
    await screen.findByLabelText('姓名')

    await userEvent.clear(screen.getByLabelText('出生时间'))
    await userEvent.clear(screen.getByLabelText(/体重/))
    await userEvent.clear(screen.getByLabelText(/身长/))
    await userEvent.clear(screen.getByLabelText(/头围/))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveBeenCalled())
    expect(sent.mock.calls[0][0]).toMatchObject({
      timeOfBirth: null,
      birthWeightKg: null,
      birthLengthCm: null,
      birthHeadCircumferenceCm: null,
    })
  })

  it('loads gestational age split into weeks and days', async () => {
    renderApp(<ProfilePage />)
    expect(await screen.findByLabelText('孕周（周）')).toHaveValue(40)
    expect(screen.getByLabelText('孕周（天）')).toHaveValue(0)
  })

  it('submits gestational age as a single day count', async () => {
    const sent = vi.fn()
    server.use(
      http.put('/api/baby', async ({ request }) => {
        const body = await request.json()
        sent(body)
        return HttpResponse.json({ ...testBaby, ...(body as object) })
      }),
    )
    renderApp(<ProfilePage />)
    await screen.findByLabelText('姓名')

    await userEvent.clear(screen.getByLabelText('孕周（周）'))
    await userEvent.type(screen.getByLabelText('孕周（周）'), '38')
    await userEvent.clear(screen.getByLabelText('孕周（天）'))
    await userEvent.type(screen.getByLabelText('孕周（天）'), '4')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveBeenCalled())
    expect(sent.mock.calls[0][0]).toMatchObject({ gestationalAgeDays: 38 * 7 + 4 })
  })

  it('sends null when gestational age is cleared', async () => {
    const sent = vi.fn()
    server.use(
      http.put('/api/baby', async ({ request }) => {
        const body = await request.json()
        sent(body)
        return HttpResponse.json({ ...testBaby, ...(body as object) })
      }),
    )
    renderApp(<ProfilePage />)
    await screen.findByLabelText('姓名')
    await userEvent.clear(screen.getByLabelText('孕周（周）'))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(sent).toHaveBeenCalled())
    expect(sent.mock.calls[0][0]).toMatchObject({ gestationalAgeDays: null })
  })

  it('reports a rejected date-of-birth change', async () => {
    server.use(http.put('/api/baby', () => apiError(400, 'DOB_AFTER_RECORDS')))
    renderApp(<ProfilePage />)
    await screen.findByLabelText('姓名')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('出生日期晚于已有的记录')
  })
})
