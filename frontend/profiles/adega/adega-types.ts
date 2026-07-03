import type { AdegaCategoryId } from './adega-categories'

/**
 * Opção/modifier de um item de cardápio (espelha AdegaMenuOption do backend).
 * Agrupada por groupLabel (ex.: "Volume", "Temperatura"); priceDeltaCents soma ao preço base.
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

/** Item de cardápio (espelha AdegaMenuItem do backend) + suas opções. */
export type MenuItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: AdegaCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
  options: MenuOption[]
}

/** Cupom de desconto gerido pelo tenant (espelha AdegaCoupon do backend). */
export type Coupon = {
  id: string
  code: string
  kind: 'percent' | 'fixed'
  value: number
  minOrderCents: number
  maxUses: number | null
  uses: number
  validUntil: string | null
  active: boolean
}

/** Configuração de fidelidade (a cada N pedidos entregues, o próximo ganha um desconto). */
export type LoyaltyConfig = {
  enabled: boolean
  thresholdOrders: number
  rewardKind: 'percent' | 'fixed'
  rewardValue: number
}

/** Status de um pedido (espelha AdegaOrderStatus). Ordem fixa. */
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

/** Item de um pedido (snapshot de nome+preço) + as opções escolhidas. */
export type OrderItem = {
  id: string
  menuItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
  options: OrderItemOption[]
}

/**
 * Pedido (espelha AdegaOrder). rejectionReason preenchido só quando status = recusado.
 * ESCAPADA adega (+18): ageConfirmed é o selo de conformidade — o backend recusa criar um
 * pedido sem confirmação de maioridade, então é sempre true nos pedidos existentes.
 */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  subtotalCents: number
  deliveryFeeCents: number
  discountCents?: number
  totalCents: number
  couponCode?: string | null
  loyaltyApplied?: boolean
  deliveryAddress: string
  notes: string | null
  rejectionReason: string | null
  ageConfirmed: boolean
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
