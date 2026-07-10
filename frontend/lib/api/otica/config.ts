import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/otica/otica-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  examDurationMinutes: number
  minOrderCents: number
  leadTimeDaysDefault: number
  examReminderEnabled?: boolean
  pickupFollowupEnabled?: boolean
  pickupFollowupDays?: number
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/otica/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/otica/config', { method: 'PUT', body: JSON.stringify(input) })
}
