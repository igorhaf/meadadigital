import { apiFetch } from '@/lib/api/client'
import type { AtelieConfig } from '@/profiles/atelie/atelie-types'

export type UpdateConfigInput = {
  businessName?: string | null
  notes?: string | null
}

export function getConfig(): Promise<AtelieConfig> {
  return apiFetch<AtelieConfig>('/api/atelie/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<AtelieConfig> {
  return apiFetch<AtelieConfig>('/api/atelie/config', { method: 'PUT', body: JSON.stringify(input) })
}
