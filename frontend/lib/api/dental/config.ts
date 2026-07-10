import { apiFetch } from '@/lib/api/client'
import type { ClinicConfig } from '@/profiles/dental/dental-types'

export type UpdateConfigInput = {
  durationMinutes: number
  bufferMinutes: number
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  recallEnabled: boolean
  recallMonths: number
}

export function getConfig(): Promise<ClinicConfig> {
  return apiFetch<ClinicConfig>('/api/dental/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<ClinicConfig> {
  return apiFetch<ClinicConfig>('/api/dental/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
