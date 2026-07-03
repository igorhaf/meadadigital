import { apiFetch } from '@/lib/api/client'

/** Config do delivery (espelha ModaInfantilConfig). Valores em centavos. */
export type ModaInfantilConfig = {
  deliveryFeeCents: number
  minOrderCents: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
}

export function getConfig(): Promise<ModaInfantilConfig> {
  return apiFetch<ModaInfantilConfig>('/api/moda-infantil/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<ModaInfantilConfig> {
  return apiFetch<ModaInfantilConfig>('/api/moda-infantil/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
