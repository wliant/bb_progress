import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiSend, apiUpload, ApiError } from './client'
import type {
  Baby,
  BabyInput,
  CareLog,
  CareType,
  Gender,
  GrowthMeasure,
  GrowthRecord,
  GrowthRecordInput,
  GrowthStandards,
  MilestoneAchievement,
  MilestoneAgeGroup,
  NewbornAssessment,
} from './types'

export function useBaby() {
  return useQuery<Baby | null>({
    queryKey: ['baby'],
    queryFn: async () => {
      try {
        return await apiGet<Baby>('/api/baby')
      } catch (e) {
        if (e instanceof ApiError && e.code === 'BABY_NOT_FOUND') return null
        throw e
      }
    },
  })
}

export function useSaveBaby() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (baby: BabyInput) => apiSend<Baby>('PUT', '/api/baby', baby),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['baby'] })
      // Birth measurements and gestational age both feed the newborn assessment.
      queryClient.invalidateQueries({ queryKey: ['newborn-assessment'] })
      queryClient.invalidateQueries({ queryKey: ['growth-records'] })
    },
  })
}

export function useUploadBabyPhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => apiUpload<Baby>('/api/baby/photo', file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['baby'] }),
  })
}

export function useGrowthRecords() {
  return useQuery<GrowthRecord[]>({
    queryKey: ['growth-records'],
    queryFn: () => apiGet('/api/growth-records'),
  })
}

export function useGrowthStandards(gender: Gender | undefined, measure: GrowthMeasure) {
  return useQuery<GrowthStandards>({
    queryKey: ['growth-standards', gender, measure],
    queryFn: () => apiGet(`/api/growth-standards?gender=${gender}&measure=${measure}`),
    enabled: gender !== undefined,
    staleTime: Infinity,
  })
}

/** Null when the profile lacks a gestational age or birth measurements. */
export function useNewbornAssessment() {
  return useQuery<NewbornAssessment | null>({
    queryKey: ['newborn-assessment'],
    queryFn: async () => {
      try {
        return await apiGet<NewbornAssessment>('/api/newborn-assessment')
      } catch (e) {
        if (
          e instanceof ApiError &&
          ['GESTATIONAL_AGE_NOT_SET', 'BIRTH_MEASUREMENTS_NOT_SET', 'BABY_NOT_FOUND'].includes(e.code)
        ) {
          return null
        }
        throw e
      }
    },
  })
}

export function useSaveGrowthRecord() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: GrowthRecordInput }) =>
      id
        ? apiSend<GrowthRecord>('PUT', `/api/growth-records/${id}`, input)
        : apiSend<GrowthRecord>('POST', '/api/growth-records', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['growth-records'] }),
  })
}

export function useDeleteGrowthRecord() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiSend('DELETE', `/api/growth-records/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['growth-records'] }),
  })
}

export function useMilestones() {
  return useQuery<MilestoneAgeGroup[]>({
    queryKey: ['milestones'],
    queryFn: () => apiGet('/api/milestones'),
  })
}

export function useSetMilestoneAchievement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, achievedOn, note }: { id: string; achievedOn: string; note?: string }) =>
      apiSend<MilestoneAchievement>('PUT', `/api/milestones/${id}/achievement`, { achievedOn, note }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['milestones'] }),
  })
}

export function useRemoveMilestoneAchievement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiSend('DELETE', `/api/milestones/${id}/achievement`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['milestones'] }),
  })
}

export function useUploadMilestonePhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) =>
      apiUpload<MilestoneAchievement>(`/api/milestones/${id}/achievement/photo`, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['milestones'] }),
  })
}

export function useCareLogs(date: string) {
  return useQuery<CareLog[]>({
    queryKey: ['care-logs', date],
    queryFn: () => apiGet(`/api/care-logs?date=${date}`),
  })
}

export function useCreateCareLog() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { type: CareType; loggedAt?: string; note?: string }) =>
      apiSend<CareLog>('POST', '/api/care-logs', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['care-logs'] }),
  })
}

export function useUpdateCareLog() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, loggedAt, note }: { id: string; loggedAt: string; note?: string }) =>
      apiSend<CareLog>('PUT', `/api/care-logs/${id}`, { loggedAt, note }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['care-logs'] }),
  })
}

export function useDeleteCareLog() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiSend('DELETE', `/api/care-logs/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['care-logs'] }),
  })
}

export function useUploadCareLogPhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) =>
      apiUpload<CareLog>(`/api/care-logs/${id}/photo`, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['care-logs'] }),
  })
}

export function useRemoveCareLogPhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiSend<CareLog>('DELETE', `/api/care-logs/${id}/photo`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['care-logs'] }),
  })
}
