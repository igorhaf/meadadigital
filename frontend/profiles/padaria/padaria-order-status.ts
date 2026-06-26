/**
 * Status de um pedido padaria (delivery/retirada iFood-style, camada 8.8) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/padaria/orders/PadariaOrderStatus.java.
 *
 * O PadariaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de padaria_orders.status trava os mesmos ids no banco.
 *
 * Gate de aceite humano (nunca da IA) + FUNIL QUE DIVERGE pela forma de entrega. Transições:
 *   aguardando   → em_preparo, recusado, cancelado
 *   em_preparo   → pronto, cancelado
 *   pronto       → retirado (retirada), saiu_entrega (entrega), cancelado
 *   saiu_entrega → entregue, cancelado
 *   retirado/entregue/recusado/cancelado → (terminal)
 */
export const PADARIA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'retirado', label: 'Retirado' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type PadariaOrderStatus = (typeof PADARIA_ORDER_STATUSES)[number]
export type PadariaOrderStatusId = PadariaOrderStatus['id']

export const ALLOWED_NEXT: Record<PadariaOrderStatusId, PadariaOrderStatusId[]> = {
  aguardando: ['em_preparo', 'recusado', 'cancelado'],
  em_preparo: ['pronto', 'cancelado'],
  pronto: ['retirado', 'saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  retirado: [],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return PADARIA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
