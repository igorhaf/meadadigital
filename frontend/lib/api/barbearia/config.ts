import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/barbearia/barber-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  slotMinutes: number
  queueEnabled: boolean
  reminderEnabled?: boolean
  autoCompleteEnabled?: boolean
  upsellEnabled?: boolean
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/barbearia/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/barbearia/config', { method: 'PUT', body: JSON.stringify(input) })
}
