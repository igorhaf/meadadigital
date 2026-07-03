import { apiFetch } from '@/lib/api/client'
import type { BarberLoyaltyConfig } from '@/profiles/barbearia/barber-types'

export function getLoyalty(): Promise<BarberLoyaltyConfig> {
  return apiFetch<BarberLoyaltyConfig>('/api/barbearia/loyalty')
}

export function updateLoyalty(input: {
  enabled: boolean
  thresholdCuts: number
}): Promise<BarberLoyaltyConfig> {
  return apiFetch<BarberLoyaltyConfig>('/api/barbearia/loyalty', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
