import { apiFetch } from '@/lib/api/client'

/**
 * Config da padaria (espelha PadariaConfig). Valores monetários em centavos.
 * ESCAPADA (8.8): leadTimeDaysDefault — prazo padrão (em dias) dos itens sob encomenda que não
 * declaram um leadTimeDays próprio.
 */
export type PadariaConfig = {
  deliveryFeeCents: number
  minOrderCents: number
  leadTimeDaysDefault: number
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
  leadTimeDaysDefault: number
}

export function getConfig(): Promise<PadariaConfig> {
  return apiFetch<PadariaConfig>('/api/padaria/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<PadariaConfig> {
  return apiFetch<PadariaConfig>('/api/padaria/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
