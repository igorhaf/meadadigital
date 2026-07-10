import { apiFetch } from '@/lib/api/client'
import type { Coupon } from '@/profiles/adega/adega-types'

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
  return apiFetch<{ items: Coupon[] }>('/api/adega/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<Coupon> {
  return apiFetch<Coupon>('/api/adega/coupons', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<Coupon> {
  return apiFetch<Coupon>(`/api/adega/coupons/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCoupon(id: string): Promise<Coupon> {
  return apiFetch<Coupon>(`/api/adega/coupons/${id}/toggle`, { method: 'PATCH' })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/adega/coupons/${id}`, { method: 'DELETE' })
}
