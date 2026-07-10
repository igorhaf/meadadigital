import { apiFetch } from '@/lib/api/client'
import type { OficinaConfig } from '@/profiles/oficina/oficina-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  returnReminderEnabled?: boolean
  returnReminderDays?: number
}

export function getConfig(): Promise<OficinaConfig> {
  return apiFetch<OficinaConfig>('/api/oficina/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<OficinaConfig> {
  return apiFetch<OficinaConfig>('/api/oficina/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
