import { apiFetch } from '@/lib/api/client'
import type { LoyaltyConfig } from '@/profiles/lavanderia/lavanderia-types'

export function getLoyalty(): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/lavanderia/loyalty')
}

export function updateLoyalty(input: LoyaltyConfig): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/lavanderia/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
