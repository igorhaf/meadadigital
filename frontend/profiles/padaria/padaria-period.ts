/**
 * Período de retirada/entrega do perfil padaria (camada 8.8, ESCAPADA 1) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/padaria/PadariaPeriod.java.
 *
 * Pedido sob encomenda carrega o dia (pickup_or_delivery_date) + a FAIXA do dia (este enum).
 * O PadariaPeriodParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK de padaria_orders.delivery_period trava os mesmos ids no banco. Clone do
 * floricultura-period.ts.
 */
export const PADARIA_PERIODS = [
  { id: 'manha', label: 'Manhã (8h–12h)' },
  { id: 'tarde', label: 'Tarde (13h–18h)' },
] as const

export type PadariaPeriod = (typeof PADARIA_PERIODS)[number]
export type PadariaPeriodId = PadariaPeriod['id']

/** Rótulo pt-BR de um período (fallback: o próprio id se desconhecido). */
export function periodLabel(id: string): string {
  return PADARIA_PERIODS.find((p) => p.id === id)?.label ?? id
}
