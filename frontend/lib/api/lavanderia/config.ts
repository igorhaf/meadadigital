import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha LavanderiaConfig). Valores em centavos; turnaround em dias. */
export type LavanderiaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
  turnaroundDaysDefault: number
  expressEnabled: boolean
  expressSurchargePct: number
  expressTurnaroundDays: number
  collectReminderEnabled: boolean
  readyReminderEnabled: boolean
  readyReminderDays: number
  reactivationEnabled: boolean
  reactivationDays: number
  reactivationCouponCode: string | null
}

export type UpdateConfigInput = LavanderiaConfig

export function getConfig(): Promise<LavanderiaConfig> {
  return apiFetch<LavanderiaConfig>('/api/lavanderia/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<LavanderiaConfig> {
  return apiFetch<LavanderiaConfig>('/api/lavanderia/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
