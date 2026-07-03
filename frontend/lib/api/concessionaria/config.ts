import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/concessionaria/concessionaria-types'

export type UpdateConfigInput = {
  businessName?: string | null
  durationMinutes: number
  bufferMinutes: number
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  notes?: string | null
  followupEnabled?: boolean
  followupDays?: number
  testdriveReminderEnabled?: boolean
  autoCompleteEnabled?: boolean
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/concessionaria/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/concessionaria/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
