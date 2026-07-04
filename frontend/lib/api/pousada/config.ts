import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/pousada/pousada-types'

export type UpdateConfigInput = {
  checkInTime: string // "HH:MM"
  checkOutTime: string // "HH:MM"
  cancellationPolicy?: string | null
  reminderEnabled?: boolean
  autoTransitionEnabled?: boolean
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/pousada/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/pousada/config', { method: 'PUT', body: JSON.stringify(input) })
}
