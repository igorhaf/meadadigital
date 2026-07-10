import { apiFetch } from '@/lib/api/client'
import type { LoyaltyConfig } from '@/profiles/pizzaria/pizzaria-types'

export function getLoyalty(): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/pizzaria/loyalty')
}

export function updateLoyalty(input: LoyaltyConfig): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/pizzaria/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
