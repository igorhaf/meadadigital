import { apiFetch } from '@/lib/api/client'

/**
 * Config da papelaria (espelha PapelariaConfig). Valores monetários em centavos.
 * ESCAPADA (8.15): leadTimeDaysDefault — prazo padrão (em dias) dos itens sob encomenda que não
 * declaram um leadTimeDays próprio.
 */
export type PapelariaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
  leadTimeDaysDefault: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
  leadTimeDaysDefault: number
}

export function getConfig(): Promise<PapelariaConfig> {
  return apiFetch<PapelariaConfig>('/api/papelaria/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<PapelariaConfig> {
  return apiFetch<PapelariaConfig>('/api/papelaria/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
