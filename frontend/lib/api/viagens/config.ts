import { apiFetch } from '@/lib/api/client'
import type { Config } from '@/profiles/viagens/viagens-types'

export type UpdateConfigInput = {
  businessName?: string | null
  notes?: string | null
}

export function getConfig(): Promise<Config> {
  return apiFetch<Config>('/api/viagens/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<Config> {
  return apiFetch<Config>('/api/viagens/config', { method: 'PUT', body: JSON.stringify(input) })
}
