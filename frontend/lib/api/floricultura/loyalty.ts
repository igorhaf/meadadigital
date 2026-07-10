import { apiFetch } from '@/lib/api/client'
import type { LoyaltyConfig } from '@/profiles/floricultura/floricultura-types'

export function getLoyalty(): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/floricultura/loyalty')
}

export function updateLoyalty(input: LoyaltyConfig): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/floricultura/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
