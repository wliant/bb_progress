import { describe, expect, it } from 'vitest'
import i18next from 'i18next'
import { careSummary, formatMinutes, noteAddsDetail } from './careFormat'
import type { CareLog } from '../api/types'
import '../i18n'

const t = i18next.t.bind(i18next) as never

const base: CareLog = {
  id: 'c1',
  type: 'FEEDING',
  loggedAt: '2026-05-10T09:30:00+08:00',
  note: null,
  method: null,
  amountMl: null,
  durationMin: null,
  startedAt: null,
  endedAt: null,
  sleepMinutes: null,
  condition: null,
  media: [],
}

describe('careSummary', () => {
  it('reads a bottle feed as its volume', () => {
    expect(careSummary(t, { ...base, method: 'BOTTLE', amountMl: 110 })).toBe('110 ml')
  })

  it('reads a breastfeed with both quantities as minutes and ml', () => {
    expect(careSummary(t, { ...base, method: 'BREAST', durationMin: 15, amountMl: 60 })).toBe(
      '15 min · 60 ml',
    )
  })

  it('says the amount was not recorded rather than implying a feed of nothing', () => {
    // The shape of every historical breastfeed: minutes, and no volume ever measured.
    expect(careSummary(t, { ...base, method: 'BREAST', durationMin: 15 })).toContain('未记录奶量')
  })

  it('falls back to null for a quick-logged entry with no detail yet', () => {
    expect(careSummary(t, base)).toBeNull()
  })

  it('reads an open sleep as still asleep, not as zero minutes', () => {
    const open = { ...base, type: 'SLEEP' as const, startedAt: base.loggedAt }
    expect(careSummary(t, open)).toBe('仍在睡')
  })

  it('reads a closed sleep as its derived duration', () => {
    const closed = { ...base, type: 'SLEEP' as const, startedAt: base.loggedAt, sleepMinutes: 95 }
    expect(careSummary(t, closed)).toBe('睡了 1小时 35分')
  })

  it('names the diaper condition', () => {
    expect(careSummary(t, { ...base, type: 'DIAPER', condition: 'VERY_DIRTY' })).toBe('很脏')
  })
})

describe('formatMinutes', () => {
  it('drops the hours under an hour and the minutes on the hour', () => {
    expect(formatMinutes(t, 25)).toBe('25分')
    expect(formatMinutes(t, 120)).toBe('2小时')
    expect(formatMinutes(t, 95)).toBe('1小时 35分')
  })
})

describe('noteAddsDetail', () => {
  it('hides a legacy note that only repeats the quantity now in the fields', () => {
    // These are the exact shapes V11 parsed; the text stays in the database as the
    // audit trail, but showing it beside the value would read "20 ml · 20ml".
    for (const note of ['20ml', '110ml', '30 ml', 'Left 15, right 7', 'Right 10 mins', '100']) {
      expect(noteAddsDetail(note)).toBe(false)
    }
  })

  it('keeps genuine prose, which no field captures', () => {
    for (const note of [
      'Baby fell asleep',
      '90ml (first time of bottle feeding)',
      'Latching but not very efficient. Give formula 20ml',
      'Nothing much',
    ]) {
      expect(noteAddsDetail(note)).toBe(true)
    }
  })

  it('treats an absent or empty note as adding nothing', () => {
    expect(noteAddsDetail(null)).toBe(false)
    expect(noteAddsDetail('   ')).toBe(false)
  })
})
