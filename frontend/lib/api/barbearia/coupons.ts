import { apiFetch } from '@/lib/api/client'
import type { BarberCoupon } from '@/profiles/barbearia/barber-types'

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

export function listCoupons(): Promise<{ items: BarberCoupon[] }> {
  return apiFetch<{ items: BarberCoupon[] }>('/api/barbearia/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<BarberCoupon> {
  return apiFetch<BarberCoupon>('/api/barbearia/coupons', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCoupon(id: string, input: UpdateCouponInput): Promise<BarberCoupon> {
  return apiFetch<BarberCoupon>(`/api/barbearia/coupons/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleCoupon(id: string, active: boolean): Promise<BarberCoupon> {
  return apiFetch<BarberCoupon>(`/api/barbearia/coupons/${id}/toggle`, {
    method: 'PATCH', body: JSON.stringify({ active }),
  })
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/barbearia/coupons/${id}`, { method: 'DELETE' })
}
