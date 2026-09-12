import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { GrowthPage } from './GrowthPage'
import { renderApp } from '../test/render'
import { apiError, server } from '../test/server'

describe('GrowthPage', () => {
  it('renders measure tabs, the chart container and the record list', async () => {
    renderApp(<GrowthPage />)
    expect(await screen.findByRole('tab', { name: '体重' })).toBeInTheDocument()
    expect(await screen.findByTestId('growth-chart-WEIGHT')).toBeInTheDocument()
    expect(await screen.findByText('2026-03-15')).toBeInTheDocument()
  })

  it('validates that at least one measurement is entered', async () => {
    renderApp(<GrowthPage />)
    await userEvent.click(await screen.findByRole('button', { name: '添加记录' }))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('至少填写一项测量值')).toBeInTheDocument()
  })

  it('reports a duplicate date from the server', async () => {
    server.use(http.post('/api/growth-records', () => apiError(400, 'DUPLICATE_DATE')))
    renderApp(<GrowthPage />)
    await userEvent.click(await screen.findByRole('button', { name: '添加记录' }))
    await userEvent.type(screen.getByRole('spinbutton', { name: /体重/ }), '6.2')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('这一天已经有记录了')
  })

  it('prompts to create the profile when there is none', async () => {
    server.use(http.get('/api/baby', () => apiError(404, 'BABY_NOT_FOUND')))
    renderApp(<GrowthPage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('shows an error state when records fail to load', async () => {
    server.use(http.get('/api/growth-records', () => apiError(500, 'INTERNAL_ERROR')))
    renderApp(<GrowthPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('加载失败')
  })
})
