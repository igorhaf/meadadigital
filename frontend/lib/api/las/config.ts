import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha LasConfig). Valores em centavos. */
export type LasConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<LasConfig> {
  return apiFetch<LasConfig>('/api/las/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<LasConfig> {
  return apiFetch<LasConfig>('/api/las/config', { method: 'PATCH', body: JSON.stringify(input) })
}
