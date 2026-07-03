import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha FloriculturaConfig). Valores em centavos. */
export type FloriculturaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<FloriculturaConfig> {
  return apiFetch<FloriculturaConfig>('/api/floricultura/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<FloriculturaConfig> {
  return apiFetch<FloriculturaConfig>('/api/floricultura/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
