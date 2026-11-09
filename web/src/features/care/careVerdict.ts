import type { TFunction } from 'i18next'
import type { DayAggregate, ReferenceRange } from '../../api/types'

export type VerdictKind = 'within' | 'below' | 'above' | 'notComparable'

export interface Verdict {
  kind: VerdictKind
  text: string
}

function place(t: TFunction, value: number, range: ReferenceRange): Verdict {
  const kind = value < range.low ? 'below' : value > range.high ? 'above' : 'within'
  return { kind, text: t(`care.trends.reference.${kind}`) }
}

/**
 * How a day's intake sits against the reference, or why it cannot be said.
 *
 * The "cannot be said" branch is the point of spec 10: a day with an unmeasured breastfeed
 * has an understated total, so calling it "below range" would invent a shortfall. Returns
 * null when there is no reference to compare against at all.
 */
export function intakeVerdict(t: TFunction, day: DayAggregate): Verdict | null {
  const range = day.reference?.intakeMl
  if (!range || day.feedsTotal === 0) return null
  if (!day.comparable.intake) {
    const missing = day.feedsTotal - day.feedsWithAmount
    return {
      kind: 'notComparable',
      text:
        missing > 0
          ? t('care.trends.reference.notComparableIntake', { count: missing })
          : t('care.trends.reference.notComparableToday'),
    }
  }
  return place(t, day.amountMl, range)
}

/** Same rule for sleep: an open nap is an unknown number of minutes. */
export function sleepVerdict(t: TFunction, day: DayAggregate): Verdict | null {
  const range = day.reference?.sleepMinutes
  if (!range) return null
  if (!day.comparable.sleep) {
    return {
      kind: 'notComparable',
      text:
        day.sleepSessions === 0
          ? t('care.trends.reference.notComparableSleepNone')
          : day.openSleeps > 0
            ? t('care.trends.reference.notComparableSleep')
            : t('care.trends.reference.notComparableToday'),
    }
  }
  return place(t, day.sleepMinutes, range)
}

/** The wet floor is a minimum, so there is no "above" to report. */
export function wetVerdict(t: TFunction, day: DayAggregate): Verdict | null {
  const min = day.reference?.wetNappiesMin
  if (min == null) return null
  if (!day.comparable.wet) {
    const missing = day.wetUnknown
    return {
      kind: 'notComparable',
      text:
        day.diaperTotal === 0
          ? t('care.trends.reference.notComparableToday')
          : t('care.trends.reference.notComparableWet', { count: missing }),
    }
  }
  return {
    kind: day.wetCount < min ? 'below' : 'within',
    text: t(`care.trends.reference.${day.wetCount < min ? 'below' : 'within'}`),
  }
}
