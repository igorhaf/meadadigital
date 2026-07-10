import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/cursos/cursos-types'

export type UpdateConfigInput = {
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  notes?: string | null
  nudgeEnabled: boolean
  nudgeDays: number
  certificateBaseUrl: string | null
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/cursos/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/cursos/config', { method: 'PUT', body: JSON.stringify(input) })
}
