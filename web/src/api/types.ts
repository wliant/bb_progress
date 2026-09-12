export type Gender = 'MALE' | 'FEMALE'
export type GrowthMeasure = 'WEIGHT' | 'HEIGHT' | 'HEAD_CIRCUMFERENCE'
export type CareType = 'FEEDING' | 'SLEEP' | 'DIAPER'
export type MilestoneCategory = 'SOCIAL' | 'LANGUAGE' | 'COGNITIVE' | 'MOTOR'

export interface Baby {
  name: string
  dateOfBirth: string
  gender: Gender
  hasPhoto: boolean
}

export interface GrowthRecord {
  id: string
  measuredOn: string
  weightKg: number | null
  heightCm: number | null
  headCircumferenceCm: number | null
  note: string | null
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
}

export interface ApiErrorBody {
  status: number
  code: string
  message: string
  fieldErrors?: { field: string; code: string; message: string }[]
}
