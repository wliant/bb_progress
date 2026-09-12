import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MilestonesPage } from './MilestonesPage'
import { renderApp } from '../test/render'

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
})
