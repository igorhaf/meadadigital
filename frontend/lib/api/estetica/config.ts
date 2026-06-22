import { apiFetch } from '@/lib/api/client'
import type { AestheticConfig } from '@/profiles/estetica/estetica-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  slotMinutes: number
}

export function getConfig(): Promise<AestheticConfig> {
  return apiFetch<AestheticConfig>('/api/estetica/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<AestheticConfig> {
  return apiFetch<AestheticConfig>('/api/estetica/config', { method: 'PUT', body: JSON.stringify(input) })
}
