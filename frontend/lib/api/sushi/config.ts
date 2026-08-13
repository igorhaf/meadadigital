import { apiFetch } from '@/lib/api/client'

/**
 * Config do restaurante sushi (espelha SushiRestaurantConfig). Valores em centavos.
 * schedulingEnabled: quando true, aceita pedidos agendados (data + período).
 */
export type SushiConfig = {
  deliveryFeeCents: number
  minOrderCents: number
  schedulingEnabled: boolean
  upsellEnabled: boolean
  reactivationEnabled: boolean
  reactivationDays: number
  reactivationCouponCode: string | null
}

export type UpdateConfigInput = {
  deliveryFeeCents: number
  minOrderCents: number
  schedulingEnabled: boolean
  upsellEnabled?: boolean
  reactivationEnabled?: boolean
  reactivationDays?: number
  reactivationCouponCode?: string | null
}

export function getConfig(): Promise<SushiConfig> {
  return apiFetch<SushiConfig>('/api/sushi/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<SushiConfig> {
  return apiFetch<SushiConfig>('/api/sushi/config', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
