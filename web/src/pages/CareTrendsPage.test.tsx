import { describe, expect, it } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { CareTrendsPage } from './CareTrendsPage'
import { renderApp } from '../test/render'
import { apiError, server, testCareAggregates } from '../test/server'

describe('CareTrendsPage', () => {
  it('shows the total intake with the coverage that qualifies it', async () => {
    renderApp(<CareTrendsPage />)
    expect(await screen.findByText('1,020 ml')).toBeInTheDocument()
    // The headline total never stands alone: 14 of 17 feeds carried a volume. The zh
    // string names the denominator first — "来自 17 次喂养中的 14 次（82.4%）".
    expect(screen.getByText(/17 次喂养中的 14 次（82\.4%）/)).toBeInTheDocument()
  })

  it('names the partial state in the legend, so a hatched bar can be read', async () => {
    renderApp(<CareTrendsPage />)
    expect(await screen.findByText('部分 — 有喂养未记录奶量')).toBeInTheDocument()
    expect(screen.getByText('已记录')).toBeInTheDocument()
  })

  it('explains that unmeasured breastfeeds are left out rather than counted as zero', async () => {
    renderApp(<CareTrendsPage />)
    expect(await screen.findByText(/未记录奶量的亲喂不计入总量/)).toBeInTheDocument()
  })

  it('says a sleep is still open instead of dropping it silently', async () => {
    renderApp(<CareTrendsPage />)
    expect(await screen.findByText(/尚未结束/)).toBeInTheDocument()
  })

  it('offers a table view carrying every figure, with coverage per partial day', async () => {
    renderApp(<CareTrendsPage />)
    await userEvent.click(await screen.findByRole('button', { name: /显示数据表/ }))
    const table = screen.getByRole('table')
    expect(table).toBeInTheDocument()
    // The partial day shows its measured/total alongside the volume.
    expect(screen.getByText(/\(6\/9\)/)).toBeInTheDocument()
  })

  it('requests the range the user picks', async () => {
    const requested: string[] = []
    server.use(
      http.get('/api/care-aggregates', ({ request }) => {
        const url = new URL(request.url)
        requested.push(`${url.searchParams.get('from')}..${url.searchParams.get('to')}`)
        return HttpResponse.json(testCareAggregates)
      }),
    )
    renderApp(<CareTrendsPage />)
    await screen.findByText('1,020 ml')
    await userEvent.click(screen.getByRole('button', { name: '7 天' }))
    await screen.findByText('1,020 ml')

    // 30 days is the default; the 7-day window is six days back from today inclusive.
    const [first, second] = requested
    const span = (r: string) => {
      const [from, to] = r.split('..')
      return (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1
    }
    expect(span(first)).toBe(30)
    expect(span(second)).toBe(7)
  })

  it('renders an error in place rather than an empty chart', async () => {
    server.use(http.get('/api/care-aggregates', () => apiError(400, 'CARE_AGGREGATE_RANGE_INVALID')))
    renderApp(<CareTrendsPage />)
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('日期范围无效')).toBeInTheDocument()
  })
})

describe('CareTrendsPage reference ranges', () => {
  it('shows the typical-range legend and the disclaimer by default', async () => {
    renderApp(<CareTrendsPage />)
    // One per chart carrying a band, so assert on presence rather than uniqueness.
    expect((await screen.findAllByText('常见范围')).length).toBeGreaterThan(0)
    expect(screen.getByText(/并非针对您宝宝的建议/)).toBeInTheDocument()
  })

  it('hides every reference element when the toggle is off, and remembers it', async () => {
    renderApp(<CareTrendsPage />)
    const toggle = await screen.findByLabelText('显示参考范围')
    expect(toggle).toBeChecked()

    await userEvent.click(toggle)
    expect(screen.queryAllByText('常见范围')).toHaveLength(0)
    expect(screen.queryByText(/并非针对您宝宝的建议/)).not.toBeInTheDocument()
    expect(localStorage.getItem('bb.trends.showReference')).toBe('off')

    // The preference is read back on the next mount.
    cleanup()
    renderApp(<CareTrendsPage />)
    expect(await screen.findByLabelText('显示参考范围')).not.toBeChecked()
    localStorage.clear()
  })

  it('says the weight is being carried forward rather than implying it was measured', async () => {
    renderApp(<CareTrendsPage />)
    expect(await screen.findByText(/体重沿用/)).toBeInTheDocument()
  })

  it('notes that nothing in the sleep range can be compared yet', async () => {
    renderApp(<CareTrendsPage />)
    // Fixture has one closed-sleep day and one open; the open one blocks a verdict, and
    // sleep must never render as a deficit while anything is unknown.
    expect((await screen.findAllByText('每日睡眠')).length).toBeGreaterThan(0)
    expect(screen.queryByText(/低于常见范围/)).not.toBeInTheDocument()
  })

  it('charts wet nappies against the suggested minimum', async () => {
    renderApp(<CareTrendsPage />)
    expect((await screen.findAllByText('每日尿湿次数')).length).toBeGreaterThan(0)
    expect(screen.getByText('建议下限')).toBeInTheDocument()
    expect(screen.getByText(/应用无法验证/)).toBeInTheDocument()
  })
})
