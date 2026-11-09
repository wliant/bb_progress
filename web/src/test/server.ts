import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import type { Baby, CareLog, GrowthRecord, GrowthStandards, MilestoneAgeGroup } from '../api/types'

export const testBaby: Baby = {
  name: '小测试',
  dateOfBirth: '2026-01-15',
  gender: 'FEMALE',
  timeOfBirth: '14:30:00',
  gestationalAgeDays: 280,
  hasPhoto: false,
  photoVersion: null,
  birthWeightKg: 3.25,
  birthLengthCm: 49.5,
  birthHeadCircumferenceCm: 34.0,
}

export const testGrowthRecords: GrowthRecord[] = [
  {
    id: 'r1',
    measuredOn: '2026-03-15',
    weightKg: 5.4,
    heightCm: 58.5,
    headCircumferenceCm: null,
    note: null,
    birth: false,
  },
  {
    id: 'r0',
    measuredOn: '2026-01-15',
    weightKg: 3.25,
    heightCm: 49.5,
    headCircumferenceCm: 34.0,
    note: null,
    birth: true,
  },
]

const p = (values: number[]) => values.map((value, ageMonths) => ({ ageMonths, value }))

export const testStandards: GrowthStandards = {
  measure: 'WEIGHT',
  gender: 'FEMALE',
  curves: {
    p3: p([2.4, 3.2, 4.0]),
    p15: p([2.8, 3.6, 4.5]),
    p50: p([3.2, 4.2, 5.1]),
    p85: p([3.7, 4.8, 5.9]),
    p97: p([4.2, 5.4, 6.6]),
  },
}

export const testMilestones: MilestoneAgeGroup[] = [
  {
    ageMonths: 2,
    milestones: [
      {
        id: '2m-social-smiles',
        category: 'SOCIAL',
        titleEn: 'Smiles when you talk to or smile at them',
        titleZh: '你对宝宝说话或微笑时会报以微笑',
        achievement: null,
      },
      {
        id: '2m-motor-head-up',
        category: 'MOTOR',
        titleEn: 'Holds head up when on tummy',
        titleZh: '趴着时能抬头',
        achievement: { achievedOn: '2026-03-01', note: null, media: [] },
      },
    ],
  },
]

export const testNewbornAssessment = {
  gestationalAgeDays: 280,
  standard: 'INTERGROWTH-21st Newborn Size Standards',
  covered: true,
  assessments: [
    { measure: 'WEIGHT' as const, value: 3.25, centile: 42.1, zScore: -0.2 },
    { measure: 'HEIGHT' as const, value: 49.5, centile: 38.4, zScore: -0.29 },
    { measure: 'HEAD_CIRCUMFERENCE' as const, value: 34.0, centile: 45.3, zScore: -0.12 },
  ],
}

export const testCareLogs: CareLog[] = [
  {
    id: 'c1',
    type: 'FEEDING',
    loggedAt: '2026-05-10T09:30:00+08:00',
    note: null,
    method: 'BOTTLE',
    amountMl: 150,
    durationMin: null,
    startedAt: null,
    endedAt: null,
    sleepMinutes: null,
    condition: null,
    media: [],
  },
]

/**
 * Two days that between them cover the case the aggregate view exists to handle: one
 * where every feed was measured, and one where some were not, so its total understates
 * the day.
 */
export const testCareAggregates = {
  from: '2026-05-09',
  to: '2026-05-10',
  days: [
    {
      date: '2026-05-09',
      amountMl: 600,
      feedsTotal: 8,
      feedsWithAmount: 8,
      breastMinutes: 0,
      partial: false,
      sleepMinutes: 95,
      sleepSessions: 1,
      openSleeps: 0,
      diaperTotal: 2,
      diaperClean: 1,
      diaperSlightlyDirty: 1,
      diaperDirty: 0,
      diaperVeryDirty: 0,
      diaperUnknown: 0,
    },
    {
      date: '2026-05-10',
      amountMl: 420,
      feedsTotal: 9,
      feedsWithAmount: 6,
      breastMinutes: 48,
      partial: true,
      sleepMinutes: 0,
      sleepSessions: 1,
      openSleeps: 1,
      diaperTotal: 1,
      diaperClean: 0,
      diaperSlightlyDirty: 0,
      diaperDirty: 0,
      diaperVeryDirty: 0,
      diaperUnknown: 1,
    },
  ],
  summary: {
    amountMl: 1020,
    feedsTotal: 17,
    feedsWithAmount: 14,
    breastMinutes: 48,
    sleepMinutes: 95,
    diaperTotal: 3,
    daysWithData: 2,
    coveragePct: 82.4,
    avgAmountMlPerDay: 510,
    avgFeedsPerDay: 8.5,
    avgSleepMinutesPerDay: 47.5,
  },
}

/** Mirrors the backend's error contract: every failure carries a stable `code`. */
export function apiError(status: number, code: string) {
  return HttpResponse.json({ status, code, message: code }, { status })
}

export const testMedia = [
  {
    id: 'profile',
    source: 'PROFILE' as const,
    kind: 'PHOTO' as const,
    url: '/api/baby/photo?v=p1',
    thumbnailUrl: '/api/baby/photo?size=thumb&v=p1',
    takenOn: null,
    takenAt: null,
    careType: null,
    titleEn: null,
    titleZh: null,
    note: null,
  },
  {
    id: 'media:m1',
    source: 'MILESTONE' as const,
    kind: 'PHOTO' as const,
    url: '/api/media/m1/content',
    thumbnailUrl: '/api/media/m1/content?size=thumb',
    takenOn: '2026-06-01',
    takenAt: null,
    careType: null,
    titleEn: 'Smiles when you talk to or smile at them',
    titleZh: '你对宝宝说话或微笑时会报以微笑',
    note: null,
  },
  {
    id: 'media:c1',
    source: 'CARE_LOG' as const,
    kind: 'PHOTO' as const,
    url: '/api/media/c1/content',
    thumbnailUrl: '/api/media/c1/content?size=thumb',
    takenOn: '2026-05-10',
    takenAt: '2026-05-10T09:30:00+08:00',
    careType: 'FEEDING' as const,
    titleEn: null,
    titleZh: null,
    note: '150ml',
  },
  {
    id: 'media:v1',
    source: 'CARE_LOG' as const,
    kind: 'VIDEO' as const,
    url: '/api/media/v1/content',
    thumbnailUrl: null,
    takenOn: '2026-05-10',
    takenAt: '2026-05-10T09:29:00+08:00',
    careType: 'FEEDING' as const,
    titleEn: null,
    titleZh: null,
    note: '翻身',
  },
]

export const handlers = [
  http.get('/api/baby', () => HttpResponse.json(testBaby)),
  http.get('/api/growth-records', () => HttpResponse.json(testGrowthRecords)),
  http.get('/api/growth-standards', () => HttpResponse.json(testStandards)),
  http.get('/api/milestones', () => HttpResponse.json(testMilestones)),
  http.get('/api/newborn-assessment', () => HttpResponse.json(testNewbornAssessment)),
  http.get('/api/care-logs', () => HttpResponse.json(testCareLogs)),
  http.get('/api/media', () => HttpResponse.json(testMedia)),
  http.get('/api/care-aggregates', () => HttpResponse.json(testCareAggregates)),
]

export const server = setupServer(...handlers)
