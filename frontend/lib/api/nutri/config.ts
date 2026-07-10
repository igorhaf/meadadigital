import { apiFetch } from '@/lib/api/client'
import type { NutriConfig } from '@/profiles/nutri/nutri-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  bufferMinutes: number
  reminderEnabled?: boolean
  autoCompleteEnabled?: boolean
  reengagementEnabled?: boolean
  reengagementDays?: number
}

export function getConfig(): Promise<NutriConfig> {
  return apiFetch<NutriConfig>('/api/nutri/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<NutriConfig> {
  return apiFetch<NutriConfig>('/api/nutri/config', { method: 'PUT', body: JSON.stringify(input) })
}
