import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { NewbornAssessmentCard, formatGestationalAge } from './NewbornAssessmentCard'
import { renderApp } from '../../test/render'
import { apiError, server, testNewbornAssessment } from '../../test/server'

describe('formatGestationalAge', () => {
  it('renders days as weeks+days', () => {
    expect(formatGestationalAge(280)).toBe('40+0')
    expect(formatGestationalAge(275)).toBe('39+2')
    expect(formatGestationalAge(231)).toBe('33+0')
  })
})

describe('NewbornAssessmentCard', () => {
  it('shows each birth measurement with its centile and gestational age', async () => {
    renderApp(<NewbornAssessmentCard />)

    expect(await screen.findByText('出生体格评估')).toBeInTheDocument()
    expect(screen.getByText('孕 40+0 周')).toBeInTheDocument()
    expect(screen.getByText('第 42.1 百分位')).toBeInTheDocument()
    expect(screen.getByText(/3.25 公斤/)).toBeInTheDocument()
    expect(screen.getByText(/INTERGROWTH-21st/)).toBeInTheDocument()
  })

  it('signs the z-score so above and below the median are distinguishable', async () => {
    server.use(
      http.get('/api/newborn-assessment', () =>
        HttpResponse.json({
          ...testNewbornAssessment,
          assessments: [{ measure: 'WEIGHT', value: 4.1, centile: 91.2, zScore: 1.35 }],
        }),
      ),
    )
    renderApp(<NewbornAssessmentCard />)
    expect(await screen.findByText('z +1.35')).toBeInTheDocument()
  })

  // The bundled standard starts at 33+0; extrapolating below it would be wrong.
  it('explains when the gestational age is outside the bundled standard', async () => {
    server.use(
      http.get('/api/newborn-assessment', () =>
        HttpResponse.json({ ...testNewbornAssessment, gestationalAgeDays: 210, covered: false, assessments: [] }),
      ),
    )
    renderApp(<NewbornAssessmentCard />)

    expect(await screen.findByText(/不在本应用内置的标准范围内/)).toBeInTheDocument()
    expect(screen.queryByText(/百分位/)).not.toBeInTheDocument()
  })

  it('renders nothing when gestational age has not been recorded', async () => {
    server.use(http.get('/api/newborn-assessment', () => apiError(404, 'GESTATIONAL_AGE_NOT_SET')))
    const { container } = renderApp(<NewbornAssessmentCard />)

    await new Promise((resolve) => setTimeout(resolve, 30))
    expect(container.querySelector('[data-testid=newborn-assessment]')).toBeNull()
  })

  it('renders in English too', async () => {
    renderApp(<NewbornAssessmentCard />, { language: 'en' })
    expect(await screen.findByText('Size at birth')).toBeInTheDocument()
    expect(screen.getByText('at 40+0 weeks')).toBeInTheDocument()
    expect(screen.getByText('Centile 42.1')).toBeInTheDocument()
  })
})
