/**
 * Status de um pedido comida (delivery iFood-style, camada 8.4) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/comida/orders/ComidaOrderStatus.java.
 *
 * O ComidaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de comida_orders.status trava os mesmos ids no banco.
 *
 * ESCAPADA 1 (gate de aceite do restaurante — ação HUMANA no painel, nunca da IA). Transições:
 *   aguardando   → em_preparo, recusado
 *   em_preparo   → saiu_entrega, cancelado
 *   saiu_entrega → entregue, cancelado
 *   entregue/recusado/cancelado → (terminal)
 */
export const COMIDA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type ComidaOrderStatus = (typeof COMIDA_ORDER_STATUSES)[number]
export type ComidaOrderStatusId = ComidaOrderStatus['id']

export const ALLOWED_NEXT: Record<ComidaOrderStatusId, ComidaOrderStatusId[]> = {
  aguardando: ['em_preparo', 'recusado'],
  em_preparo: ['saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return COMIDA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
