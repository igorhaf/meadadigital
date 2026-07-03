import type { PizzariaCategoryId } from './pizzaria-categories'

/**
 * Opção/modifier de um item de cardápio (ESCAPADA 2 — espelha PizzariaMenuOption do backend).
 * Agrupada por groupLabel (ex.: "Tamanho", "Borda"); priceDeltaCents soma ao preço base.
 */
export type MenuOption = {
  id: string
  menuItemId: string
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  available: boolean
  sortOrder: number
}

/** Item de cardápio (espelha PizzariaMenuItem do backend) + suas opções. */
export type MenuItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: PizzariaCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
  options: MenuOption[]
}

/** Status de um pedido (espelha PizzariaOrderStatus). Ordem fixa. */
export type OrderStatus =
  'aguardando' | 'em_preparo' | 'saiu_entrega' | 'entregue' | 'recusado' | 'cancelado'

/** Opção escolhida num item de pedido (snapshot de label+delta no momento do pedido). */
export type OrderItemOption = {
  id: string
  menuOptionId: string | null
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
}

/**
 * Sabor de uma pizza meio-a-meio (ESCAPADA estrutural — espelha PizzariaOrderItemFlavor do backend).
 * Cada fração (fractionIndex) é um sabor distinto; o snapshot guarda nome+preço do sabor no momento
 * do pedido. O unitPriceCents do item JÁ embute a regra do MAIS CARO — aqui é só exibição.
 */
export type OrderItemFlavor = {
  id: string
  menuItemId: string
  fractionIndex: number
  flavorName: string
  flavorPriceCents: number
}

/** Item de um pedido (snapshot de nome+preço) + as opções escolhidas + os sabores (meio-a-meio). */
export type OrderItem = {
  id: string
  menuItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
  options: OrderItemOption[]
  flavors: OrderItemFlavor[]
}

/** Pedido (espelha PizzariaOrder). rejectionReason preenchido só quando status = recusado. */
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
