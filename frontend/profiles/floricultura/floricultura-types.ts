import type { FloriculturaCategoryId } from './floricultura-categories'

/**
 * Opção/modifier de um item de cardápio (ESCAPADA 2 — espelha FloriculturaCatalogOption do backend).
 * Agrupada por groupLabel (ex.: "Tamanho", "Adicionais"); priceDeltaCents soma ao preço base.
 */
export type CatalogOption = {
  id: string
  catalogItemId: string
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  available: boolean
  sortOrder: number
}

/** Item de cardápio (espelha FloriculturaCatalogItem do backend) + suas opções. */
export type CatalogItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: FloriculturaCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
  options: CatalogOption[]
}

/** Status de um pedido (espelha FloriculturaOrderStatus). Ordem fixa. */
export type OrderStatus =
  'aguardando' | 'em_preparo' | 'saiu_entrega' | 'entregue' | 'recusado' | 'cancelado'

/** Opção escolhida num item de pedido (snapshot de label+delta no momento do pedido). */
export type OrderItemOption = {
  id: string
  catalogOptionId: string | null
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
}

/** Item de um pedido (snapshot de nome+preço) + as opções escolhidas. */
export type OrderItem = {
  id: string
  catalogItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
  options: OrderItemOption[]
}

/** Pedido (espelha FloriculturaOrder). rejectionReason preenchido só quando status = recusado. */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  subtotalCents: number
  deliveryFeeCents: number
  totalCents: number
  deliveryAddress: string
  notes: string | null
  rejectionReason: string | null
  // ESCAPADA (8.5): entrega agendada pra outra pessoa, com cartão.
  deliveryDate: string
  deliveryPeriod: string
  recipientName: string
  cardMessage: string | null
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { id: OrderStatus; label: string }[] = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
]

/**
 * Próximo status no fluxo (botão "Avançar"); null se terminal.
 * aguardando → em_preparo é o "aceitar"; recusar é botão SEPARADO (não está aqui).
 */
export const NEXT_STATUS: Record<OrderStatus, OrderStatus | null> = {
  aguardando: 'em_preparo',
  em_preparo: 'saiu_entrega',
  saiu_entrega: 'entregue',
  entregue: null,
  recusado: null,
  cancelado: null,
}

/** Rótulo pt-BR de um status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  aguardando: 'Aguardando aceite',
  em_preparo: 'Em preparo',
  saiu_entrega: 'Saiu pra entrega',
  entregue: 'Entregue',
  recusado: 'Recusado',
  cancelado: 'Cancelado',
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}
