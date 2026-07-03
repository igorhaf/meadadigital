import { apiFetch } from '@/lib/api/client'
import type { WeddingCoupon } from '@/profiles/casamento/casamento-types'

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

export function listCoupons(): Promise<{ items: WeddingCoupon[] }> {
  return apiFetch<{ items: WeddingCoupon[] }>('/api/casamento/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<WeddingCoupon> {
  return apiFetch<WeddingCoupon>('/api/casamento/coupons', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<WeddingCoupon> {
  return apiFetch<WeddingCoupon>(`/api/casamento/coupons/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCoupon(id: string, active: boolean): Promise<WeddingCoupon> {
  return apiFetch<WeddingCoupon>(`/api/casamento/coupons/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/coupons/${id}`, { method: 'DELETE' })
}
