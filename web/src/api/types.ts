export type Gender = 'MALE' | 'FEMALE'
export type GrowthMeasure = 'WEIGHT' | 'HEIGHT' | 'HEAD_CIRCUMFERENCE'
export type CareType = 'FEEDING' | 'SLEEP' | 'DIAPER'
export type MilestoneCategory = 'SOCIAL' | 'LANGUAGE' | 'COGNITIVE' | 'MOTOR'

export interface Baby {
  name: string
  dateOfBirth: string
  /** ISO local time from the API, e.g. "14:30:00"; null when not recorded. */
  timeOfBirth: string | null
  gender: Gender
  hasPhoto: boolean
  /** Changes whenever the photo is replaced; used to bust the browser's image cache. */
  photoVersion: string | null
  birthWeightKg: number | null
  birthLengthCm: number | null
  birthHeadCircumferenceCm: number | null
}

export interface BabyInput {
  name: string
  dateOfBirth: string
  timeOfBirth?: string | null
  gender: Gender
  birthWeightKg?: number | null
  birthLengthCm?: number | null
  birthHeadCircumferenceCm?: number | null
}

export interface GrowthRecord {
  id: string
  measuredOn: string
  weightKg: number | null
  heightCm: number | null
  headCircumferenceCm: number | null
  note: string | null
  /** The record owned by the profile's birth measurements. */
  birth: boolean
}

export interface GrowthRecordInput {
  measuredOn: string
  weightKg?: number | null
  heightCm?: number | null
  headCircumferenceCm?: number | null
  note?: string | null
}

export interface CurvePoint {
  ageMonths: number
  value: number
}

export interface GrowthStandards {
  measure: GrowthMeasure
  gender: Gender
  curves: Record<'p3' | 'p15' | 'p50' | 'p85' | 'p97', CurvePoint[]>
}

export interface MilestoneAchievement {
  achievedOn: string
  note: string | null
  hasPhoto: boolean
  photoVersion: string | null
}

export interface Milestone {
  id: string
  category: MilestoneCategory
  titleEn: string
  titleZh: string
  achievement: MilestoneAchievement | null
}

export interface MilestoneAgeGroup {
  ageMonths: number
  milestones: Milestone[]
}

export interface CareLog {
  id: string
  type: CareType
  loggedAt: string
  note: string | null
  hasPhoto: boolean
  photoVersion: string | null
}

export interface ApiErrorBody {
  status: number
  code: string
  message: string
  fieldErrors?: { field: string; code: string; message: string }[]
}
