import { apiFetch } from '@/lib/api/client'
import type { PetConfig } from '@/profiles/pet/pet-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  bufferMinutes: number
  reminderEnabled?: boolean
}

export function getConfig(): Promise<PetConfig> {
  return apiFetch<PetConfig>('/api/pet/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<PetConfig> {
  return apiFetch<PetConfig>('/api/pet/config', { method: 'PUT', body: JSON.stringify(input) })
}
