import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/academia/academia-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/academia/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/academia/config', { method: 'PUT', body: JSON.stringify(input) })
}
