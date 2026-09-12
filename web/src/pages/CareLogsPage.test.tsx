import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http } from 'msw'
import { CareLogsPage } from './CareLogsPage'
import { renderApp } from '../test/render'
import { apiError, server } from '../test/server'

describe('CareLogsPage', () => {
  it('lists the day entries with SGT times', async () => {
    renderApp(<CareLogsPage />)
    expect(await screen.findByText('09:30')).toBeInTheDocument()
    expect(screen.getByText(/150ml/)).toBeInTheDocument()
  })

  it('prompts to create the profile when there is none', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<CareLogsPage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('shows an error state when the day fails to load', async () => {
    server.use(http.get('/api/care-logs', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<CareLogsPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
  })
})
