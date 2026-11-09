import { describe, expect, it } from 'vitest'
import i18next from 'i18next'
import { intakeVerdict, sleepVerdict, wetVerdict } from './careVerdict'
import type { DayAggregate } from '../../api/types'
import '../../i18n'

const t = i18next.t.bind(i18next) as never

const day = (over: Partial<DayAggregate> = {}): DayAggregate => ({
  date: '2026-11-09',
  amountMl: 0,
  feedsTotal: 0,
  feedsWithAmount: 0,
  breastMinutes: 0,
  partial: false,
  sleepMinutes: 0,
  sleepSessions: 0,
  openSleeps: 0,
  diaperTotal: 0,
  diaperClean: 0,
  diaperSlightlyDirty: 0,
  diaperDirty: 0,
  diaperVeryDirty: 0,
  diaperUnknown: 0,
  wetCount: 0,
  wetUnknown: 0,
  reference: {
    weightKg: 4.7,
    weightEstimated: false,
    intakeMl: { low: 564, high: 846 },
    sleepMinutes: { low: 840, high: 1020 },
    wetNappiesMin: 6,
  },
  comparable: { intake: false, sleep: false, wet: false },
  ...over,
})

describe('intakeVerdict', () => {
  it('places a fully measured day against the band', () => {
    const d = day({ amountMl: 700, feedsTotal: 8, feedsWithAmount: 8, comparable: { intake: true, sleep: false, wet: false } })
    expect(intakeVerdict(t, d)?.kind).toBe('within')
    expect(intakeVerdict(t, day({ ...d, amountMl: 400 }))?.kind).toBe('below')
    expect(intakeVerdict(t, day({ ...d, amountMl: 900 }))?.kind).toBe('above')
  })

  it('refuses a verdict when a feed went unmeasured, and says how many', () => {
    // The whole point of spec 10: 580 ml from 6 of 11 feeds is not a shortfall.
    const d = day({ amountMl: 580, feedsTotal: 11, feedsWithAmount: 6 })
    const v = intakeVerdict(t, d)
    expect(v?.kind).toBe('notComparable')
    expect(v?.text).toContain('5')
  })

  it('refuses a verdict on a day still in progress even when fully measured', () => {
    const d = day({ amountMl: 20, feedsTotal: 1, feedsWithAmount: 1 })
    expect(intakeVerdict(t, d)?.kind).toBe('notComparable')
    expect(intakeVerdict(t, d)?.text).toContain('今天')
  })

  it('has nothing to say without a reference or without feeds', () => {
    expect(intakeVerdict(t, day({ reference: null }))).toBeNull()
    expect(intakeVerdict(t, day())).toBeNull()
  })
})

describe('sleepVerdict', () => {
  it('refuses a verdict while a nap is open, since the minutes are unknown', () => {
    const d = day({ sleepSessions: 2, openSleeps: 1, sleepMinutes: 300 })
    expect(sleepVerdict(t, d)?.kind).toBe('notComparable')
  })

  it('says nothing was logged rather than reporting a deficit', () => {
    // 8 open-ended entries and 0 minutes is the live state; it must not read as "below".
    const v = sleepVerdict(t, day())
    expect(v?.kind).toBe('notComparable')
    expect(v?.text).not.toContain('低于')
  })

  it('places a day whose sleeps are all closed', () => {
    const d = day({ sleepMinutes: 900, sleepSessions: 4, comparable: { intake: false, sleep: true, wet: false } })
    expect(sleepVerdict(t, d)?.kind).toBe('within')
  })
})

describe('wetVerdict', () => {
  it('is a floor, so a high count is still just "within"', () => {
    const d = day({ diaperTotal: 9, wetCount: 9, comparable: { intake: false, sleep: false, wet: true } })
    expect(wetVerdict(t, d)?.kind).toBe('within')
  })

  it('reports below only when every change carried a wet flag', () => {
    const d = day({ diaperTotal: 3, wetCount: 3, comparable: { intake: false, sleep: false, wet: true } })
    expect(wetVerdict(t, d)?.kind).toBe('below')
  })

  it('refuses a verdict when some change has no wet flag, and says how many', () => {
    const d = day({ diaperTotal: 4, wetCount: 2, wetUnknown: 2 })
    const v = wetVerdict(t, d)
    expect(v?.kind).toBe('notComparable')
    expect(v?.text).toContain('2')
  })
})
