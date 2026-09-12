import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MediaPage } from './MediaPage'
import { renderApp } from '../test/render'
import { apiError, server, testMedia } from '../test/server'

describe('MediaPage', () => {
  it('groups items by date with the undated profile photo first', async () => {
    renderApp(<MediaPage />)

    expect(await screen.findByText('媒体')).toBeInTheDocument()
    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings).toEqual(['档案照片', '2026-06-01', '2026-05-10'])
    expect(screen.getByText('共 4 项')).toBeInTheDocument()
  })

  it('uses thumbnails for photos and a kind tile for video', async () => {
    renderApp(<MediaPage />)

    const thumbs = await screen.findAllByRole('img')
    expect(thumbs).toHaveLength(3) // the three photos; the video has no thumbnail
    for (const thumb of thumbs) {
      expect(thumb).toHaveAttribute('src', expect.stringContaining('size=thumb'))
      expect(thumb).toHaveAttribute('loading', 'lazy')
    }
    expect(screen.getAllByText('视频').length).toBeGreaterThan(0)
  })

  it('plays a video inline in the viewer', async () => {
    renderApp(<MediaPage />)
    await screen.findByText('媒体')
    await userEvent.click(screen.getAllByRole('button')[3]) // the video tile

    const dialog = screen.getByRole('dialog')
    const video = dialog.querySelector('video')
    expect(video).not.toBeNull()
    expect(video).toHaveAttribute('controls')
    expect(video).toHaveAttribute('src', '/api/media/v1/content')
    expect(within(dialog).getByText('视频')).toBeInTheDocument()
  })

  it('plays a voice note inline in the viewer', async () => {
    server.use(
      http.get('/api/media', () =>
        HttpResponse.json([{ ...testMedia[3], id: 'media:a1', kind: 'AUDIO',
          url: '/api/media/a1/content', thumbnailUrl: null }]),
      ),
    )
    renderApp(<MediaPage />)
    await userEvent.click(await screen.findByRole('button', { name: /录音/ }))

    const audio = screen.getByRole('dialog').querySelector('audio')
    expect(audio).not.toBeNull()
    expect(audio).toHaveAttribute('controls')
    expect(audio).toHaveAttribute('src', '/api/media/a1/content')
  })

  it('captions a care-log item with its type and note', async () => {
    renderApp(<MediaPage />)
    expect(await screen.findByText('喂奶 · 150ml')).toBeInTheDocument()
  })

  it('shows the milestone title in the active language', async () => {
    const { unmount } = renderApp(<MediaPage />)
    expect(await screen.findByText('你对宝宝说话或微笑时会报以微笑')).toBeInTheDocument()
    unmount()

    renderApp(<MediaPage />, { language: 'en' })
    expect(await screen.findByText('Smiles when you talk to or smile at them')).toBeInTheDocument()
  })

  it('links from the viewer back to the entry', async () => {
    renderApp(<MediaPage />)
    await screen.findByText('媒体')
    await userEvent.click(screen.getAllByRole('button')[2]) // the care-log photo

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('link', { name: '查看原记录' })).toHaveAttribute(
      'href',
      '/care?date=2026-05-10',
    )
  })

  it('explains where media comes from when there is none', async () => {
    server.use(http.get('/api/media', () => HttpResponse.json([])))
    renderApp(<MediaPage />)

    expect(await screen.findByText('还没有任何内容')).toBeInTheDocument()
    expect(screen.getByText(/会自动出现在这里/)).toBeInTheDocument()
  })

  it('shows an error state when the gallery fails to load', async () => {
    server.use(http.get('/api/media', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<MediaPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
  })

  it('prompts to create the profile when there is none', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<MediaPage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('renders in English', async () => {
    renderApp(<MediaPage />, { language: 'en' })
    expect(await screen.findByText('Media')).toBeInTheDocument()
    expect(screen.getByText('4 items')).toBeInTheDocument()
  })
})
