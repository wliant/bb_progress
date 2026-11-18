import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { HeadShapePage } from './HeadShapePage'
import { renderApp } from '../test/render'
import { apiError, server, testHeadShapeEntries } from '../test/server'

describe('HeadShapePage', () => {
  it('lists each day newest first with its photos and note', async () => {
    renderApp(<HeadShapePage />)

    expect(await screen.findByText('头型记录')).toBeInTheDocument()
    const days = screen.getAllByText(/^2026-/).map((el) => el.textContent)
    expect(days).toEqual(['2026-06-02', '2026-05-26'])
    expect(screen.getByText('这一周一直朝右边睡')).toBeInTheDocument()
    expect(screen.getByText('2 张照片')).toBeInTheDocument()
  })

  it('requests thumbnails lazily rather than the full photos', async () => {
    renderApp(<HeadShapePage />)

    const thumbs = await screen.findAllByRole('img')
    expect(thumbs).toHaveLength(3)
    for (const thumb of thumbs) {
      expect(thumb).toHaveAttribute('src', expect.stringContaining('size=thumb'))
      expect(thumb).toHaveAttribute('loading', 'lazy')
    }
  })

  it('creates the day and uploads the photos to it in one go', async () => {
    let createdFor: string | null = null
    let uploadedCount = 0
    server.use(
      http.post('/api/head-shape', async ({ request }) => {
        const body = (await request.json()) as { recordedOn: string }
        createdFor = body.recordedOn
        return HttpResponse.json({ id: 'h9', recordedOn: body.recordedOn, note: null, media: [] })
      }),
      http.post('/api/head-shape/h9/media', async ({ request }) => {
        uploadedCount = (await request.formData()).getAll('files').length
        return HttpResponse.json([])
      }),
    )
    renderApp(<HeadShapePage />)
    await screen.findByText('头型记录')

    await userEvent.clear(screen.getByLabelText('日期'))
    await userEvent.type(screen.getByLabelText('日期'), '2026-06-03')
    await userEvent.upload(screen.getByTestId('head-shape-input'), [
      new File(['a'], 'top.jpg', { type: 'image/jpeg' }),
      new File(['b'], 'back.jpg', { type: 'image/jpeg' }),
    ])

    await waitFor(() => expect(uploadedCount).toBe(2))
    expect(createdFor).toBe('2026-06-03')
  })

  it('deletes a single photo from the viewer without touching the day', async () => {
    let deleted: string | null = null
    let removed = false
    server.use(
      http.get('/api/head-shape', () =>
        HttpResponse.json(
          removed
            ? [{ ...testHeadShapeEntries[0], media: [testHeadShapeEntries[0].media[1]] }]
            : testHeadShapeEntries,
        ),
      ),
      http.delete('/api/media/:id', ({ params }) => {
        deleted = params.id as string
        removed = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(<HeadShapePage />)
    await screen.findByText('头型记录')

    await userEvent.click(screen.getAllByRole('img')[0])
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '移除' }))

    await waitFor(() => expect(deleted).toBe('hm1'))
    expect(await screen.findByText('1 张照片')).toBeInTheDocument()
  })

  it('edits the day note from the dialog opened on its header', async () => {
    let saved: unknown = null
    server.use(
      http.put('/api/head-shape/h1', async ({ request }) => {
        saved = await request.json()
        return HttpResponse.json({ ...testHeadShapeEntries[0], note: '戴头盔第一周' })
      }),
    )
    renderApp(<HeadShapePage />)
    await screen.findByText('头型记录')

    await userEvent.click(screen.getByRole('button', { name: '编辑 2026-06-02' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.clear(within(dialog).getByRole('textbox'))
    await userEvent.type(within(dialog).getByRole('textbox'), '戴头盔第一周')
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(saved).toEqual({ recordedOn: '2026-06-02', note: '戴头盔第一周' }),
    )
  })

  it('deletes a whole day from the dialog', async () => {
    let deleted: string | null = null
    server.use(
      http.delete('/api/head-shape/:id', ({ params }) => {
        deleted = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(<HeadShapePage />)
    await screen.findByText('头型记录')

    await userEvent.click(screen.getByRole('button', { name: '编辑 2026-06-02' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '删除' }))

    await waitFor(() => expect(deleted).toBe('h1'))
  })

  it('offers photos only — no voice recorder — on the day dialog', async () => {
    renderApp(<HeadShapePage />)
    await screen.findByText('头型记录')

    await userEvent.click(screen.getByRole('button', { name: '编辑 2026-06-02' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByRole('button', { name: '录音' })).not.toBeInTheDocument()
    expect(within(dialog).getByTestId('media-input')).toHaveAttribute(
      'accept',
      'image/jpeg,image/png,image/webp',
    )
  })

  it('explains what the page is for when nothing is recorded', async () => {
    server.use(http.get('/api/head-shape', () => HttpResponse.json([])))
    renderApp(<HeadShapePage />)

    expect(await screen.findByText('还没有头型照片')).toBeInTheDocument()
  })

  it('prompts to create the profile first', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<HeadShapePage />)

    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('renders the failure in place rather than an empty page', async () => {
    server.use(http.get('/api/head-shape', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<HeadShapePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
  })

  it('renders in English', async () => {
    renderApp(<HeadShapePage />, { language: 'en' })

    expect(await screen.findByText('Head shape')).toBeInTheDocument()
    expect(screen.getByText('2 photo(s)')).toBeInTheDocument()
  })
})
