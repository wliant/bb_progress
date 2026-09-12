import { describe, expect, it } from 'vitest'
import { ageInMonths, ageParts, timeSgt } from './dates'

describe('ageParts', () => {
  it('computes whole months and remaining days', () => {
    expect(ageParts('2026-01-15', '2026-03-20')).toEqual({ months: 2, days: 5 })
  })

  it('handles the day before a month boundary', () => {
    expect(ageParts('2026-01-15', '2026-03-14')).toEqual({ months: 1, days: 27 })
  })

  it('is zero on the date of birth', () => {
    expect(ageParts('2026-01-15', '2026-01-15')).toEqual({ months: 0, days: 0 })
  })
})

describe('ageInMonths', () => {
  it('uses the WHO month length of 30.4375 days', () => {
    expect(ageInMonths('2026-01-01', '2026-01-01')).toBe(0)
    expect(ageInMonths('2026-01-01', '2026-02-01')).toBeCloseTo(31 / 30.4375, 5)
  })
})

describe('timeSgt', () => {
  it('renders SGT wall-clock time regardless of offset in the input', () => {
    expect(timeSgt('2026-05-10T09:30:00+08:00')).toBe('09:30')
    expect(timeSgt('2026-05-10T01:30:00Z')).toBe('09:30')
  })
})
