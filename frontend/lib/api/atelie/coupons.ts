import { apiFetch } from '@/lib/api/client'
import type { AtelieCoupon } from '@/profiles/atelie/atelie-types'

export type CreateCouponInput = {
  code: string
  kind: 'percent' | 'fixed'
  value: number
  minOrderCents?: number
  maxUses?: number | null
  validUntil?: string | null
  active?: boolean
}
export type UpdateCouponInput = Partial<CreateCouponInput>

export function listCoupons(): Promise<{ items: AtelieCoupon[] }> {
  return apiFetch<{ items: AtelieCoupon[] }>('/api/atelie/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<AtelieCoupon> {
  return apiFetch<AtelieCoupon>('/api/atelie/coupons', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<AtelieCoupon> {
  return apiFetch<AtelieCoupon>(`/api/atelie/coupons/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCoupon(id: string, active: boolean): Promise<AtelieCoupon> {
  return apiFetch<AtelieCoupon>(`/api/atelie/coupons/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/atelie/coupons/${id}`, { method: 'DELETE' })
}
