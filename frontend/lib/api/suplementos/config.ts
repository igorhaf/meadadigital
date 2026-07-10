import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha SuplementosConfig). Valores em centavos. */
export type SuplementosConfig = {
  deliveryFeeCents: number
  minOrderCents: number
  freeShippingThresholdCents: number | null
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
  freeShippingThresholdCents?: number | null
}

export function getConfig(): Promise<SuplementosConfig> {
  return apiFetch<SuplementosConfig>('/api/suplementos/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<SuplementosConfig> {
  return apiFetch<SuplementosConfig>('/api/suplementos/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
