import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha PizzariaConfig). Valores em centavos. */
export type PizzariaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<PizzariaConfig> {
  return apiFetch<PizzariaConfig>('/api/pizzaria/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<PizzariaConfig> {
  return apiFetch<PizzariaConfig>('/api/pizzaria/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
