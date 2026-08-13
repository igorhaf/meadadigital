import { apiFetch } from '@/lib/api/client'
import type { LoyaltyConfig } from '@/profiles/sushi/sushi-types'

export function getLoyalty(): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/sushi/loyalty')
}

export function updateLoyalty(input: LoyaltyConfig): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/sushi/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
