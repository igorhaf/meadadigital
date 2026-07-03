import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha LingerieConfig). Valores em centavos. */
export type LingerieConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<LingerieConfig> {
  return apiFetch<LingerieConfig>('/api/lingerie/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<LingerieConfig> {
  return apiFetch<LingerieConfig>('/api/lingerie/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
