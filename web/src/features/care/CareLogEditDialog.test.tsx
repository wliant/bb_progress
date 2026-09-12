import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { CareLogsPage } from '../../pages/CareLogsPage'
import { renderApp } from '../../test/render'
import { apiError, server, testCareLogs } from '../../test/server'
import type { Media } from '../../api/types'

const photo: Media = { id: 'm1', kind: 'PHOTO', contentType: 'image/jpeg',
  url: '/api/media/m1/content', thumbnailUrl: '/api/media/m1/content?size=thumb' }
const video: Media = { id: 'm2', kind: 'VIDEO', contentType: 'video/mp4',
  url: '/api/media/m2/content', thumbnailUrl: null }
const voice: Media = { id: 'm3', kind: 'AUDIO', contentType: 'audio/mp4',
  url: '/api/media/m3/content', thumbnailUrl: null }

const withMedia = (...media: Media[]) => [{ ...testCareLogs[0], media }]

describe('care log attachments', () => {
  it('offers to attach media when the entry has none', async () => {
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    expect(screen.getByRole('button', { name: '添加' })).toBeInTheDocument()
    expect(screen.getByTestId('media-input')).toHaveAttribute('multiple')
    expect(screen.getByText(/最大 200MB/)).toBeInTheDocument()
  })

  it('uploads several files at once', async () => {
    let uploadedCount = 0
    server.use(
      http.get('/api/care-logs', () =>
        HttpResponse.json(uploadedCount ? withMedia(photo, video) : withMedia()),
      ),
      http.post('/api/care-logs/:id/media', async ({ request }) => {
        const form = await request.formData()
        uploadedCount = form.getAll('files').length
        return HttpResponse.json([photo, video])
      }),
    )
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    await userEvent.upload(screen.getByTestId('media-input'), [
      new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
      new File(['b'], 'b.mp4', { type: 'video/mp4' }),
    ])

    await waitFor(() => expect(uploadedCount).toBe(2))
    // Both appear in the still-open dialog: a thumbnail for the photo, a kind tile for the video.
    await waitFor(() => {
      const dialog = screen.getByRole('dialog')
      expect(dialog.querySelector('img')).not.toBeNull()
      expect(within(dialog).getByText('视频')).toBeInTheDocument()
    })
  })

  it('shows a voice note by its kind, since it has no thumbnail', async () => {
    server.use(http.get('/api/care-logs', () => HttpResponse.json(withMedia(voice))))
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('录音')).toBeInTheDocument()
    expect(dialog.querySelector('img')).toBeNull()
  })

  it('removes one attachment without touching the others', async () => {
    const removed: string[] = []
    server.use(
      http.get('/api/care-logs', () => HttpResponse.json(withMedia(photo, video))),
      http.delete('/api/media/:id', ({ params }) => {
        removed.push(params.id as string)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const removeButtons = within(screen.getByRole('dialog')).getAllByRole('button', { name: '移除' })
    expect(removeButtons).toHaveLength(2)
    await userEvent.click(removeButtons[0])

    await waitFor(() => expect(removed).toEqual(['m1']))
  })

  it('shows the first attachment and a count on the list row', async () => {
    server.use(http.get('/api/care-logs', () => HttpResponse.json(withMedia(photo, video, voice))))
    renderApp(<CareLogsPage />)

    expect(await screen.findByText('3')).toBeInTheDocument()
    const thumb = screen.getByRole('img', { name: '照片' })
    expect(thumb).toHaveAttribute('src', expect.stringContaining('size=thumb'))
  })

  it('reports an unsupported type instead of failing silently', async () => {
    server.use(http.post('/api/care-logs/:id/media', () => apiError(400, 'UNSUPPORTED_MEDIA_TYPE')))
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    // The input carries an accept filter; bypass it so the server's own refusal is exercised.
    await userEvent.upload(screen.getByTestId('media-input'),
      new File(['x'], 'doc.pdf', { type: 'application/pdf' }), { applyAccept: false })

    expect(await screen.findByRole('alert')).toHaveTextContent('只支持照片、视频和录音')
  })

  it('reports an oversized upload', async () => {
    server.use(http.post('/api/care-logs/:id/media', () => apiError(413, 'FILE_TOO_LARGE')))
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    await userEvent.upload(screen.getByTestId('media-input'),
      new File(['x'], 'huge.mp4', { type: 'video/mp4' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('照片太大了')
  })
})
