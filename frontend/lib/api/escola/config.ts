import { apiFetch } from '@/lib/api/client'
import type { EscolaConfig } from '@/profiles/escola/escola-types'

export type UpdateConfigInput = {
  businessName?: string | null
  opensAt: string // "HH:MM"
  closesAt: string // "HH:MM"
  notes?: string | null
}

export function getConfig(): Promise<EscolaConfig> {
  return apiFetch<EscolaConfig>('/api/escola/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<EscolaConfig> {
  return apiFetch<EscolaConfig>('/api/escola/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
