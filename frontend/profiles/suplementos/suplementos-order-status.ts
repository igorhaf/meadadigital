/**
 * Status de um pedido suplementos (loja de saúde / nutrição esportiva / varejo, camada 8.24) —
 * espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/suplementos/orders/SuplementosOrderStatus.java.
 *
 * O SuplementosOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais
 * perfis). A CHECK constraint de suplementos_orders.status trava os mesmos ids no banco.
 *
 * Máquina de status com gate de aceite (ação HUMANA no painel, nunca da IA). SÓ ENTREGA (não há
 * retirada nesta camada):
 *   aguardando  → em_preparo, recusado
 *   em_preparo  → saiu_entrega, cancelado
 *   saiu_entrega → entregue, cancelado
 *   entregue/recusado/cancelado → (terminal)
 */
export const SUPLEMENTOS_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'saiu_entrega', label: 'Saiu para entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type SuplementosOrderStatus = (typeof SUPLEMENTOS_ORDER_STATUSES)[number]
export type SuplementosOrderStatusId = SuplementosOrderStatus['id']

export const ALLOWED_NEXT: Record<SuplementosOrderStatusId, SuplementosOrderStatusId[]> = {
  aguardando: ['em_preparo', 'recusado'],
  em_preparo: ['saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return SUPLEMENTOS_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
