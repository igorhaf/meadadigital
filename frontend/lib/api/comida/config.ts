import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha ComidaConfig). Valores em centavos. */
export type ComidaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<ComidaConfig> {
  return apiFetch<ComidaConfig>('/api/comida/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<ComidaConfig> {
  return apiFetch<ComidaConfig>('/api/comida/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
