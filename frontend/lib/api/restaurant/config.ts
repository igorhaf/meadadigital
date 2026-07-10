import { apiFetch } from '@/lib/api/client'
import type { ReservationConfig } from '@/profiles/restaurant/restaurant-types'

export type UpdateConfigInput = {
  durationMinutes: number
  bufferMinutes: number
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  reminderEnabled?: boolean
  autoCompleteEnabled?: boolean
}

export function getConfig(): Promise<ReservationConfig> {
  return apiFetch<ReservationConfig>('/api/restaurant/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<ReservationConfig> {
  return apiFetch<ReservationConfig>('/api/restaurant/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
