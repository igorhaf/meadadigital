import { apiFetch } from '@/lib/api/client'
import type { LoyaltyBalanceView, LoyaltyConfig } from '@/profiles/academia/academia-types'

export type UpdateLoyaltyConfigInput = {
  enabled: boolean
  pointsPerCheckin: number
  rewardThreshold?: number | null
  rewardText?: string | null
}

export function getLoyaltyConfig(): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/academia/loyalty/config')
}

export function updateLoyaltyConfig(input: UpdateLoyaltyConfigInput): Promise<LoyaltyConfig> {
  return apiFetch<LoyaltyConfig>('/api/academia/loyalty/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function getLoyaltyBalance(contactId: string): Promise<LoyaltyBalanceView> {
  return apiFetch<LoyaltyBalanceView>(
    `/api/academia/loyalty/balance?contactId=${encodeURIComponent(contactId)}`,
  )
}

export function addLoyaltyPoints(contactId: string, points: number): Promise<LoyaltyBalanceView> {
  return apiFetch<LoyaltyBalanceView>('/api/academia/loyalty/points', {
    method: 'POST',
    body: JSON.stringify({ contactId, points }),
  })
}
