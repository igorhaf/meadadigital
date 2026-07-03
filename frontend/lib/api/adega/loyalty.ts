import { apiFetch } from '@/lib/api/client'
import type { LoyaltyConfig } from '@/profiles/adega/adega-types'

export function getLoyalty(): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/adega/loyalty')
}

export function updateLoyalty(input: LoyaltyConfig): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/adega/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
