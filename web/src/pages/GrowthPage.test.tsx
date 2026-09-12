import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GrowthPage } from './GrowthPage'
import { renderApp } from '../test/render'

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
})
