import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { CareLogsPage } from '../../pages/CareLogsPage'
import { renderApp } from '../../test/render'
import { apiError, server, testCareLogs } from '../../test/server'

const withPhoto = { ...testCareLogs[0], hasPhoto: true, photoVersion: 'abc123' }

describe('care log photos', () => {
  it('offers to add a photo when the entry has none', async () => {
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const dialog = screen.getByRole('dialog')
    expect(dialog.querySelector('img')).toBeNull()
    expect(screen.getByRole('button', { name: '添加照片' })).toBeInTheDocument()
    expect(screen.getByText(/最大 25MB/)).toBeInTheDocument()
  })

  // The dialog must read the refreshed entry, not the snapshot it opened with.
  it('shows the photo in the open dialog right after uploading', async () => {
    let uploaded = false
    server.use(
      http.get('/api/care-logs', () => HttpResponse.json([uploaded ? withPhoto : testCareLogs[0]])),
      http.put('/api/care-logs/:id/photo', () => {
        uploaded = true
        return HttpResponse.json(withPhoto)
      }),
    )
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const file = new File(['x'], 'IMG_0001.jpg', { type: 'image/jpeg' })
    await userEvent.upload(screen.getByTestId('care-photo-input'), file)

    await waitFor(() => {
      expect(screen.getByRole('dialog').querySelector('img')).not.toBeNull()
    })
    expect(screen.getByRole('button', { name: '更换照片' })).toBeInTheDocument()
  })

  it('removes a photo', async () => {
    const removed = vi.fn()
    server.use(
      http.get('/api/care-logs', () => HttpResponse.json([withPhoto])),
      http.delete('/api/care-logs/:id/photo', () => {
        removed()
        return HttpResponse.json(testCareLogs[0])
      }),
    )
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))
    await userEvent.click(screen.getByRole('button', { name: '删除照片' }))

    await waitFor(() => expect(removed).toHaveBeenCalled())
  })

  it('shows a thumbnail in the list for entries that have a photo', async () => {
    server.use(http.get('/api/care-logs', () => HttpResponse.json([withPhoto])))
    renderApp(<CareLogsPage />)

    const thumb = await screen.findByRole('img', { name: '照片' })
    expect(thumb).toHaveAttribute('src', expect.stringContaining('/api/care-logs/c1/photo?v=abc123'))
  })

  it('reports an unreadable image instead of failing silently', async () => {
    server.use(http.put('/api/care-logs/:id/photo', () => apiError(400, 'IMAGE_UNREADABLE')))
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const file = new File(['x'], 'broken.jpg', { type: 'image/jpeg' })
    await userEvent.upload(screen.getByTestId('care-photo-input'), file)

    expect(await screen.findByRole('alert')).toHaveTextContent('无法读取这张图片')
  })

  it('reports an oversized upload', async () => {
    server.use(http.put('/api/care-logs/:id/photo', () => apiError(413, 'FILE_TOO_LARGE')))
    renderApp(<CareLogsPage />)
    await userEvent.click(await screen.findByText(/150ml/))

    const file = new File(['x'], 'huge.jpg', { type: 'image/jpeg' })
    await userEvent.upload(screen.getByTestId('care-photo-input'), file)

    expect(await screen.findByRole('alert')).toHaveTextContent('照片太大了')
  })
})
