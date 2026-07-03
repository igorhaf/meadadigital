import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha AdegaConfig). Valores em centavos. */
export type AdegaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<AdegaConfig> {
  return apiFetch<AdegaConfig>('/api/adega/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<AdegaConfig> {
  return apiFetch<AdegaConfig>('/api/adega/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
