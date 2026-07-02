import { apiFetch } from '@/lib/api/client'
import type { Referral } from '@/profiles/academia/academia-types'

export type ReferralStatusFilter = 'pendente' | 'convertida' | 'expirada'

export type CreateReferralInput = {
  referrerContactId?: string | null
  referredName: string
  referredPhone?: string | null
  rewardPercent?: number | null // 1..100; o CÓDIGO é gerado pelo backend
}

export function listReferrals(opts: { status?: ReferralStatusFilter } = {}): Promise<{ items: Referral[] }> {
  const qs = opts.status ? `?status=${opts.status}` : ''
  return apiFetch<{ items: Referral[] }>(`/api/academia/referrals${qs}`)
}

export function createReferral(input: CreateReferralInput): Promise<Referral> {
  return apiFetch<Referral>('/api/academia/referrals', { method: 'POST', body: JSON.stringify(input) })
}

export function convertReferral(id: string): Promise<Referral> {
  return apiFetch<Referral>(`/api/academia/referrals/${id}/convert`, { method: 'PATCH' })
}
