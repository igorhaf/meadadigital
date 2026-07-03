import { apiFetch } from '@/lib/api/client'
import type { FotografiaConfig } from '@/profiles/fotografia/fotografia-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  slotMinutes: number
}

export function getConfig(): Promise<FotografiaConfig> {
  return apiFetch<FotografiaConfig>('/api/fotografia/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<FotografiaConfig> {
  return apiFetch<FotografiaConfig>('/api/fotografia/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
