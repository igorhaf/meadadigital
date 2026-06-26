import { apiFetch } from '@/lib/api/client'
import type { DermatologiaConfig } from '@/profiles/dermatologia/dermatologia-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  bufferMinutes: number
}

export function getConfig(): Promise<DermatologiaConfig> {
  return apiFetch<DermatologiaConfig>('/api/dermatologia/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<DermatologiaConfig> {
  return apiFetch<DermatologiaConfig>('/api/dermatologia/config', { method: 'PUT', body: JSON.stringify(input) })
}
