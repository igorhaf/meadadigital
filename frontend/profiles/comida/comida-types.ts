import type { ComidaCategoryId } from './comida-categories'

/**
 * Opção/modifier de um item de cardápio (ESCAPADA 2 — espelha ComidaMenuOption do backend).
 * Agrupada por groupLabel (ex.: "Tamanho", "Adicionais"); priceDeltaCents soma ao preço base.
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

/** Item de cardápio (espelha ComidaMenuItem do backend) + suas opções. */
export type MenuItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: ComidaCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
  options: MenuOption[]
}

/** Status de um pedido (espelha ComidaOrderStatus). Ordem fixa. */
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

/** Pedido (espelha ComidaOrder). rejectionReason preenchido só quando status = recusado.
 * Onda 1 do backlog: desconto (cupom #1 + fidelidade #2, já embutido no totalCents) e zona (#8). */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  subtotalCents: number
  discountCents: number
  deliveryFeeCents: number
  totalCents: number
  couponCodeSnapshot: string | null
  loyaltyApplied: boolean
  zoneNameSnapshot: string | null
  fulfillment: 'entrega' | 'retirada'
  deliveryAddress: string | null
  notes: string | null
  rejectionReason: string | null
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
}

/** Cupom de desconto (onda 1, backlog #1 — espelha ComidaCoupon; motor sushi/adega). */
export type ComidaCoupon = {
  id: string
  companyId: string
  code: string
  kind: 'percent' | 'fixed'
  value: number
  minOrderCents: number
  maxUses: number | null
  uses: number
  validUntil: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Config de fidelidade por contagem (onda 1, backlog #2 — espelha ComidaLoyaltyConfig). */
export type ComidaLoyaltyConfig = {
  enabled: boolean
  thresholdOrders: number
  rewardKind: 'percent' | 'fixed'
  rewardValue: number
}

/** Zona de entrega com taxa própria (onda 1, backlog #8 — espelha ComidaDeliveryZone). */
export type ComidaDeliveryZone = {
  id: string
  companyId: string
  name: string
  feeCents: number
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Linhas agregadas do relatório de vendas (onda 1, backlog #15). */
export type ComidaReportRow = {
  month?: string
  item?: string
  hour?: number
  count: number
  totalCents?: number
}

export type ComidaReportSummary = {
  months: number
  totalCount: number
  totalCents: number
  avgTicketCents: number
  byMonth: ComidaReportRow[]
  topItems: ComidaReportRow[]
  byHour: ComidaReportRow[]
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
