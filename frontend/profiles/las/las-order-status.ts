/**
 * Status de um pedido las (loja de lãs / novelos / tricô-crochê — varejo, camada 8.23) — espelho
 * 1:1 de src/main/java/com/meada/whatsapp/profiles/las/orders/LasOrderStatus.java.
 *
 * O LasOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem (o
 * teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de las_orders.status trava os mesmos ids no banco.
 *
 * Máquina de status com gate de aceite (ação HUMANA no painel, nunca da IA):
 *   aguardando → separando, recusado
 *   separando  → enviado, cancelado
 *   enviado    → entregue, cancelado
 *   entregue/recusado/cancelado → (terminal)
 */
export const LAS_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'separando', label: 'Separando' },
  { id: 'enviado', label: 'Enviado' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type LasOrderStatus = (typeof LAS_ORDER_STATUSES)[number]
export type LasOrderStatusId = LasOrderStatus['id']

export const ALLOWED_NEXT: Record<LasOrderStatusId, LasOrderStatusId[]> = {
  aguardando: ['separando', 'recusado'],
  separando: ['enviado', 'cancelado'],
  enviado: ['entregue', 'cancelado'],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return LAS_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
