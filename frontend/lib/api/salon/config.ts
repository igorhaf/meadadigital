import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/salon/salon-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  bufferMinutes: number
  reminderEnabled?: boolean
  autoCompleteEnabled?: boolean
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/salon/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/salon/config', { method: 'PUT', body: JSON.stringify(input) })
}
