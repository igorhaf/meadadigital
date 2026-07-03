import { apiFetch } from '@/lib/api/client'
import type { Coupon, CouponValidation } from '@/profiles/academia/academia-types'

export type CreateCouponInput = {
  code: string
  kind: 'percent' | 'fixed'
  value: number
  minCents?: number
  maxUses?: number
  validUntil?: string // "YYYY-MM-DD"
  active?: boolean
}

export type UpdateCouponInput = {
  code?: string
  kind?: 'percent' | 'fixed'
  value?: number
  minCents?: number
  maxUses?: number
  clearMaxUses?: boolean
  validUntil?: string // "YYYY-MM-DD"
  clearValidUntil?: boolean
  active?: boolean
}

export function listCoupons(): Promise<{ items: Coupon[] }> {
  return apiFetch<{ items: Coupon[] }>('/api/academia/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<Coupon> {
  return apiFetch<Coupon>('/api/academia/coupons', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<Coupon> {
  return apiFetch<Coupon>(`/api/academia/coupons/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCoupon(id: string, active: boolean): Promise<Coupon> {
  return apiFetch<Coupon>(`/api/academia/coupons/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/academia/coupons/${id}`, { method: 'DELETE' })
}

export function validateCoupon(code: string, subtotalCents: number): Promise<CouponValidation> {
  return apiFetch<CouponValidation>('/api/academia/coupons/validate', {
    method: 'POST',
    body: JSON.stringify({ code, subtotalCents }),
  })
}
