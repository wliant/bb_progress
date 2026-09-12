import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { HomePage } from './HomePage'
import { renderApp } from '../test/render'
import { server } from '../test/server'

describe('HomePage', () => {
  it('shows the baby summary with name (Chinese UI)', async () => {
    renderApp(<HomePage />)
    expect(await screen.findByText('小测试')).toBeInTheDocument()
    expect(screen.getByText('快速记录')).toBeInTheDocument()
  })

  it('prompts to create a profile when none exists', async () => {
    server.use(
      http.get('/api/baby', () =>
        HttpResponse.json({ status: 404, code: 'BABY_NOT_FOUND', message: '' }, { status: 404 }),
      ),
    )
    renderApp(<HomePage />)
    expect(await screen.findByText('还没有宝宝档案，先创建一个吧！')).toBeInTheDocument()
  })

  it('renders in English when the language is switched', async () => {
    renderApp(<HomePage />, { language: 'en' })
    expect(await screen.findByText('小测试')).toBeInTheDocument()
    expect(screen.getByText('Quick log')).toBeInTheDocument()
  })
})
