import { apiFetch } from '@/lib/api/client'
import type { Coupon } from '@/profiles/moda-infantil/moda-infantil-types'

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

export function listCoupons(): Promise<{ items: Coupon[] }> {
  return apiFetch<{ items: Coupon[] }>('/api/moda-infantil/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<Coupon> {
  return apiFetch<Coupon>('/api/moda-infantil/coupons', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<Coupon> {
  return apiFetch<Coupon>(`/api/moda-infantil/coupons/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCoupon(id: string): Promise<Coupon> {
  return apiFetch<Coupon>(`/api/moda-infantil/coupons/${id}/toggle`, { method: 'PATCH' })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/moda-infantil/coupons/${id}`, { method: 'DELETE' })
}
