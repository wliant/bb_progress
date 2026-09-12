import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useTranslation } from 'react-i18next'
import { LanguageSwitcher } from './LanguageSwitcher'
import { renderApp } from '../test/render'

function Probe() {
  const { t } = useTranslation()
  return (
    <div>
      <LanguageSwitcher />
      <p>{t('nav.home')}</p>
    </div>
  )
}

describe('LanguageSwitcher', () => {
  it('defaults to Chinese and switches to English and back', async () => {
    renderApp(<Probe />)
    expect(screen.getByText('首页')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'English' }))
    expect(screen.getByText('Home')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '中文' }))
    expect(screen.getByText('首页')).toBeInTheDocument()
  })
})
