import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MilestonesPage } from './MilestonesPage'
import { renderApp } from '../test/render'
import { apiError, server, testMilestones } from '../test/server'

describe('MilestonesPage', () => {
  it('shows age groups with progress and expands to show Chinese titles', async () => {
    renderApp(<MilestonesPage />)
    expect(await screen.findByText('2 个月')).toBeInTheDocument()
    expect(screen.getByText('1/2')).toBeInTheDocument()

    await userEvent.click(screen.getByText('2 个月'))
    expect(screen.getByText('你对宝宝说话或微笑时会报以微笑')).toBeInTheDocument()
  })

  it('shows English titles when language is en', async () => {
    renderApp(<MilestonesPage />, { language: 'en' })
    await userEvent.click(await screen.findByText('2 months'))
    expect(screen.getByText('Smiles when you talk to or smile at them')).toBeInTheDocument()
  })

  it('opens the achievement dialog when a milestone is clicked', async () => {
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('标记达成')).toBeInTheDocument()
  })

  it('prompts to create the profile instead of showing the checklist', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<MilestonesPage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
    expect(screen.queryByText('2 个月')).not.toBeInTheDocument()
  })

  it('shows an error state, not an empty list, when milestones fail to load', async () => {
    server.use(http.get('/api/milestones', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<MilestonesPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
    expect(screen.getByText('服务器出错了，请稍后重试')).toBeInTheDocument()
  })

  it('shows an uploaded attachment without needing the dialog reopened', async () => {
    // The dialog must read the refreshed milestone, not the snapshot it opened with.
    let uploaded = false
    const achieved = (media: unknown[]) => [{
      ...testMilestones[0],
      milestones: [
        { ...testMilestones[0].milestones[0],
          achievement: { achievedOn: '2026-03-10', note: null, media } },
        testMilestones[0].milestones[1],
      ],
    }]
    server.use(
      http.get('/api/milestones', () =>
        HttpResponse.json(achieved(uploaded ? [{ id: 'm1', kind: 'PHOTO', contentType: 'image/jpeg',
          url: '/api/media/m1/content', thumbnailUrl: '/api/media/m1/content?size=thumb' }] : [])),
      ),
      http.post('/api/milestones/:id/achievement/media', () => {
        uploaded = true
        return HttpResponse.json([{ id: 'm1', kind: 'PHOTO', contentType: 'image/jpeg',
          url: '/api/media/m1/content', thumbnailUrl: '/api/media/m1/content?size=thumb' }])
      }),
    )

    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))

    expect(screen.getByRole('dialog').querySelector('img')).toBeNull()
    await userEvent.upload(screen.getByTestId('media-input'),
      new File(['x'], 'photo.png', { type: 'image/png' }))

    await waitFor(() => {
      expect(screen.getByRole('dialog').querySelector('img')).not.toBeNull()
    })
  })

  // The control used to be hidden until the milestone was already achieved, so the
  // only way to attach one was to save, reopen the dialog, and upload.
  it('offers attachments on a milestone that has not been achieved yet', async () => {
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))

    expect(screen.getByRole('button', { name: '添加' })).toBeInTheDocument()
    expect(screen.getByTestId('media-input')).toHaveAttribute('multiple')
  })

  it('marks achieved and uploads files chosen beforehand, in one save', async () => {
    const calls: string[] = []
    server.use(
      http.put('/api/milestones/:id/achievement', () => {
        calls.push('achievement')
        return HttpResponse.json({ achievedOn: '2026-03-20', note: null, media: [] })
      }),
      http.post('/api/milestones/:id/achievement/media', () => {
        calls.push('media')
        return HttpResponse.json([])
      }),
    )
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))

    await userEvent.upload(screen.getByTestId('media-input'), [
      new File(['x'], 'smile.png', { type: 'image/png' }),
      new File(['y'], 'clip.mp4', { type: 'video/mp4' }),
    ])

    // Held locally, previewed, and flagged as pending until the achievement exists.
    expect(screen.getByRole('dialog').querySelector('img')).not.toBeNull()
    expect(screen.getByText(/2 个文件会在保存后一起上传/)).toBeInTheDocument()
    expect(calls).toEqual([])

    await userEvent.click(screen.getByRole('button', { name: '标记达成' }))

    await waitFor(() => expect(calls).toEqual(['achievement', 'media']))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('uploads immediately when the milestone is already achieved', async () => {
    const calls: string[] = []
    server.use(
      http.post('/api/milestones/:id/achievement/media', () => {
        calls.push('media')
        return HttpResponse.json([])
      }),
    )
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('趴着时能抬头')) // already achieved in the fixture

    await userEvent.upload(screen.getByTestId('media-input'),
      new File(['x'], 'p.png', { type: 'image/png' }))

    await waitFor(() => expect(calls).toEqual(['media']))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('reports a failed check-off instead of failing silently', async () => {
    server.use(
      http.put('/api/milestones/:id/achievement', () => apiError(404, 'BABY_NOT_FOUND')),
    )
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))
    await userEvent.click(screen.getByRole('button', { name: '标记达成' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('请先创建宝宝档案')
  })
})
