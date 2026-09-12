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

  it('shows the uploaded photo without needing the dialog reopened', async () => {
    // The dialog must read the refreshed milestone, not the snapshot it opened with.
    let uploaded = false
    server.use(
      http.get('/api/milestones', () =>
        HttpResponse.json(
          uploaded
            ? [
                {
                  ...testMilestones[0],
                  milestones: [
                    {
                      ...testMilestones[0].milestones[0],
                      achievement: {
                        achievedOn: '2026-03-10',
                        note: null,
                        hasPhoto: true,
                        photoVersion: 'v2',
                      },
                    },
                    testMilestones[0].milestones[1],
                  ],
                },
              ]
            : [
                {
                  ...testMilestones[0],
                  milestones: [
                    {
                      ...testMilestones[0].milestones[0],
                      achievement: {
                        achievedOn: '2026-03-10',
                        note: null,
                        hasPhoto: false,
                        photoVersion: null,
                      },
                    },
                    testMilestones[0].milestones[1],
                  ],
                },
              ],
        ),
      ),
      http.put('/api/milestones/:id/achievement/photo', () => {
        uploaded = true
        return HttpResponse.json({
          achievedOn: '2026-03-10',
          note: null,
          hasPhoto: true,
          photoVersion: 'v2',
        })
      }),
    )

    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))

    const dialog = screen.getByRole('dialog')
    expect(dialog.querySelector('img')).toBeNull()

    const file = new File(['x'], 'photo.png', { type: 'image/png' })
    await userEvent.upload(dialog.querySelector('input[type=file]') as HTMLInputElement, file)

    await waitFor(() => {
      expect(screen.getByRole('dialog').querySelector('img')).not.toBeNull()
    })
  })

  // The photo control used to be hidden until the milestone was already achieved, so the
  // only way to attach one was to save, reopen the dialog, and upload.
  it('offers a photo on a milestone that has not been achieved yet', async () => {
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))

    const dialog = screen.getByRole('dialog')
    expect(screen.getByRole('button', { name: '添加照片' })).toBeInTheDocument()
    expect(dialog.querySelector('input[type=file]')).not.toBeNull()
  })

  it('marks achieved and uploads a photo chosen beforehand, in one save', async () => {
    const calls: string[] = []
    server.use(
      http.put('/api/milestones/:id/achievement', () => {
        calls.push('achievement')
        return HttpResponse.json({ achievedOn: '2026-03-20', note: null, hasPhoto: false, photoVersion: null })
      }),
      http.put('/api/milestones/:id/achievement/photo', () => {
        calls.push('photo')
        return HttpResponse.json({ achievedOn: '2026-03-20', note: null, hasPhoto: true, photoVersion: 'v1' })
      }),
    )
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('你对宝宝说话或微笑时会报以微笑'))

    const file = new File(['x'], 'smile.png', { type: 'image/png' })
    await userEvent.upload(screen.getByTestId('milestone-photo-input'), file)

    // Held locally, shown as a preview, and flagged as pending.
    expect(screen.getByRole('dialog').querySelector('img')).not.toBeNull()
    expect(screen.getByText('照片会在标记达成后一起保存')).toBeInTheDocument()
    expect(calls).toEqual([])

    await userEvent.click(screen.getByRole('button', { name: '标记达成' }))

    await waitFor(() => expect(calls).toEqual(['achievement', 'photo']))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('uploads immediately when the milestone is already achieved', async () => {
    const calls: string[] = []
    server.use(
      http.put('/api/milestones/:id/achievement/photo', () => {
        calls.push('photo')
        return HttpResponse.json({ achievedOn: '2026-03-01', note: null, hasPhoto: true, photoVersion: 'v1' })
      }),
    )
    renderApp(<MilestonesPage />)
    await userEvent.click(await screen.findByText('2 个月'))
    await userEvent.click(screen.getByText('趴着时能抬头')) // already achieved in the fixture

    const file = new File(['x'], 'p.png', { type: 'image/png' })
    await userEvent.upload(screen.getByTestId('milestone-photo-input'), file)

    await waitFor(() => expect(calls).toEqual(['photo']))
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
