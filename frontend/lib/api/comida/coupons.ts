import { apiFetch } from '@/lib/api/client'
import type { ComidaCoupon } from '@/profiles/comida/comida-types'

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

export function listCoupons(): Promise<{ items: ComidaCoupon[] }> {
  return apiFetch<{ items: ComidaCoupon[] }>('/api/comida/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<ComidaCoupon> {
  return apiFetch<ComidaCoupon>('/api/comida/coupons', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<ComidaCoupon> {
  return apiFetch<ComidaCoupon>(`/api/comida/coupons/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCoupon(id: string, active: boolean): Promise<ComidaCoupon> {
  return apiFetch<ComidaCoupon>(`/api/comida/coupons/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/comida/coupons/${id}`, { method: 'DELETE' })
}
