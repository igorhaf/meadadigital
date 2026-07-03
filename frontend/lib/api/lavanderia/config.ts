import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha LavanderiaConfig). Valores em centavos; turnaround em dias. */
export type LavanderiaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
  turnaroundDaysDefault: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
  turnaroundDaysDefault: number
}

export function getConfig(): Promise<LavanderiaConfig> {
  return apiFetch<LavanderiaConfig>('/api/lavanderia/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<LavanderiaConfig> {
  return apiFetch<LavanderiaConfig>('/api/lavanderia/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
