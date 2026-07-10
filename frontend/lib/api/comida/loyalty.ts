import { apiFetch } from '@/lib/api/client'
import type { ComidaLoyaltyConfig } from '@/profiles/comida/comida-types'

export function getLoyalty(): Promise<ComidaLoyaltyConfig> {
  return apiFetch<ComidaLoyaltyConfig>('/api/comida/loyalty')
}

export function updateLoyalty(input: ComidaLoyaltyConfig): Promise<ComidaLoyaltyConfig> {
  return apiFetch<ComidaLoyaltyConfig>('/api/comida/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
