import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import type { Baby, CareLog, GrowthRecord, GrowthStandards, MilestoneAgeGroup } from '../api/types'

export const testBaby: Baby = {
  name: '小测试',
  dateOfBirth: '2026-01-15',
  gender: 'FEMALE',
  timeOfBirth: '14:30:00',
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
        achievement: { achievedOn: '2026-03-01', note: null, hasPhoto: false, photoVersion: null },
      },
    ],
  },
]

export const testCareLogs: CareLog[] = [
  { id: 'c1', type: 'FEEDING', loggedAt: '2026-05-10T09:30:00+08:00', note: '150ml' },
]

/** Mirrors the backend's error contract: every failure carries a stable `code`. */
export function apiError(status: number, code: string) {
  return HttpResponse.json({ status, code, message: code }, { status })
}

export const handlers = [
  http.get('/api/baby', () => HttpResponse.json(testBaby)),
  http.get('/api/growth-records', () => HttpResponse.json(testGrowthRecords)),
  http.get('/api/growth-standards', () => HttpResponse.json(testStandards)),
  http.get('/api/milestones', () => HttpResponse.json(testMilestones)),
  http.get('/api/care-logs', () => HttpResponse.json(testCareLogs)),
]

export const server = setupServer(...handlers)
