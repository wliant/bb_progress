import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { PhotosPage } from './PhotosPage'
import { renderApp } from '../test/render'
import { apiError, server } from '../test/server'

describe('PhotosPage', () => {
  it('groups photos by date with the undated profile photo first', async () => {
    renderApp(<PhotosPage />)

    expect(await screen.findByText('照片')).toBeInTheDocument()
    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings).toEqual(['档案照片', '2026-06-01', '2026-05-10'])
    expect(screen.getByText('共 3 张')).toBeInTheDocument()
  })

  // Tiles must not pull the full-size images.
  it('uses the thumbnail variant for tiles and lazy-loads them', async () => {
    renderApp(<PhotosPage />)

    const tiles = await screen.findAllByRole('img')
    expect(tiles).toHaveLength(3)
    for (const tile of tiles) {
      expect(tile).toHaveAttribute('src', expect.stringContaining('size=thumb'))
      expect(tile).toHaveAttribute('loading', 'lazy')
    }
  })

  it('captions a care-log photo with its type and note', async () => {
    renderApp(<PhotosPage />)
    expect(await screen.findByText('喂奶 · 150ml')).toBeInTheDocument()
  })

  it('shows the milestone title in the active language', async () => {
    const { unmount } = renderApp(<PhotosPage />)
    expect(await screen.findByText('你对宝宝说话或微笑时会报以微笑')).toBeInTheDocument()
    unmount()

    renderApp(<PhotosPage />, { language: 'en' })
    expect(await screen.findByText('Smiles when you talk to or smile at them')).toBeInTheDocument()
  })

  it('opens a viewer with the full-size image and a link to the entry', async () => {
    renderApp(<PhotosPage />)
    const tiles = await screen.findAllByRole('img')
    await userEvent.click(tiles[2]) // the care-log photo

    const dialog = screen.getByRole('dialog')
    const full = within(dialog).getByRole('img')
    expect(full).toHaveAttribute('src', expect.not.stringContaining('size=thumb'))
    expect(within(dialog).getByText(/2026-05-10/)).toBeInTheDocument()
    expect(within(dialog).getByRole('link', { name: '查看原记录' })).toHaveAttribute(
      'href',
      '/care?date=2026-05-10',
    )
  })

  it('explains where photos come from when there are none', async () => {
    server.use(http.get('/api/photos', () => HttpResponse.json([])))
    renderApp(<PhotosPage />)

    expect(await screen.findByText('还没有照片')).toBeInTheDocument()
    expect(screen.getByText(/会自动出现在这里/)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('shows an error state when the gallery fails to load', async () => {
    server.use(http.get('/api/photos', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<PhotosPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
  })

  it('prompts to create the profile when there is none', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<PhotosPage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
    expect(screen.queryByText(/共 \d+ 张/)).not.toBeInTheDocument()
  })

  it('renders the gallery in English', async () => {
    renderApp(<PhotosPage />, { language: 'en' })
    expect(await screen.findByText('Photos')).toBeInTheDocument()
    // Appears twice: as the group heading and as the tile caption.
    expect(screen.getAllByText('Profile photo')).toHaveLength(2)
    expect(screen.getByText('3 photos')).toBeInTheDocument()
  })

})
