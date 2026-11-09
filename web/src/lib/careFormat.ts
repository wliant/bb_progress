import type { TFunction } from 'i18next'
import type { CareLog } from '../api/types'

/**
 * Whether a note says anything the structured fields do not.
 *
 * Entries logged before the app had fields carry their detail as note text — "20ml",
 * "Left 15, right 7" — and V11 parsed exactly that into the columns. The text stays in
 * the database as the audit trail for the parse, but repeating it beside the value it
 * produced would read as "20 ml · 20ml". So a note that is nothing but the quantity is
 * not rendered, while genuine prose ("Baby fell asleep") always is.
 */
export function noteAddsDetail(note: string | null): boolean {
  if (!note) return false
  const remainder = note
    .replace(/\d+(\.\d+)?/g, ' ')
    .replace(/\b(ml|mins?|minutes?|left|right|side|each|of|and|out)\b/gi, ' ')
    .replace(/[^\p{L}]/gu, '')
  return remainder.length > 0
}

/** "1h 35m", or "25m" under an hour, with the units localized. */
export function formatMinutes(t: TFunction, minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  const hours = t('care.hoursShort', { h })
  const mins = t('care.minutesShort', { m })
  if (h === 0) return mins
  return m === 0 ? hours : `${hours} ${mins}`
}

/**
 * The entry's detail as one short line, for the day list.
 *
 * Returns null when nothing was recorded — a quick-logged entry with no detail yet —
 * so the caller can fall back to the type name rather than print an empty separator.
 */
export function careSummary(t: TFunction, log: CareLog): string | null {
  switch (log.type) {
    case 'FEEDING': {
      const parts: string[] = []
      if (log.durationMin != null) parts.push(`${log.durationMin} min`)
      if (log.amountMl != null) parts.push(`${log.amountMl} ml`)
      // A breastfeed whose volume was never measured says so, rather than reading as a
      // feed of nothing. Bottle feeds always have an amount, so they need no caveat.
      if (parts.length > 0 && log.amountMl == null && log.method !== 'BOTTLE') {
        return `${parts.join(' · ')} · ${t('care.notMeasured')}`
      }
      if (parts.length === 0) return log.method ? t(`care.method.${log.method}`) : null
      return parts.join(' · ')
    }
    case 'SLEEP':
      if (log.sleepMinutes != null) {
        return t('care.duration', { duration: formatMinutes(t, log.sleepMinutes) })
      }
      return log.startedAt ? t('care.sleeping') : null
    case 'DIAPER':
      return log.condition ? t(`care.condition.${log.condition}`) : null
  }
}
