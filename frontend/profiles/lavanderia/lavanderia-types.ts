import type { LavanderiaCategoryId } from './lavanderia-categories'

/**
 * Opção/modifier de um serviço (espelha LavanderiaServiceOption do backend). Agrupada por groupLabel
 * (ex.: "Acabamento", "Cuidado"); priceDeltaCents soma ao preço base.
 */
export type ServiceOption = {
  id: string
  serviceId: string
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  available: boolean
  sortOrder: number
}

/** Serviço do catálogo (espelha LavanderiaService do backend) + suas opções. */
export type ServiceItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: LavanderiaCategoryId
  turnaroundDays: number
  careInstructions: string | null
  available: boolean
  createdAt: string
  updatedAt: string
  options: ServiceOption[]
}

/** Status de um pedido (espelha LavanderiaOrderStatus). Ordem fixa. */
export type OrderStatus =
  | 'aguardando'
  | 'coletado'
  | 'em_processo'
  | 'pronto'
  | 'saiu_entrega'
  | 'entregue'
  | 'recusado'
  | 'cancelado'

/** Opção escolhida num item de pedido (snapshot de label+delta no momento do pedido). */
export type OrderItemOption = {
  id: string
  serviceOptionId: string | null
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
}

/** Item de um pedido (snapshot de nome+preço+turnaround) + as opções escolhidas. */
export type OrderItem = {
  id: string
  serviceId: string
  serviceName: string
  qty: number
  unitPriceCents: number
  turnaroundSnapshot: number
  options: OrderItemOption[]
}

/** Cupom de desconto gerido pelo tenant (espelha LavanderiaCoupon do backend). */
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

/** Pedido (espelha LavanderiaOrder). rejectionReason preenchido só quando status = recusado. */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  subtotalCents: number
  discountCents: number
  deliveryFeeCents: number
  totalCents: number
  couponCode: string | null
  loyaltyApplied: boolean
  express: boolean
  expressSurchargeCents: number
  deliveryAddress: string
  notes: string | null
  rejectionReason: string | null
  // ESCAPADA (8.10): DUAS datas acopladas por turnaround.
  collectDate: string
  deliveryDate: string
  period: string
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { id: OrderStatus; label: string }[] = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'coletado', label: 'Coletado' },
  { id: 'em_processo', label: 'Em processo' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
]

/**
 * Próximo status no fluxo (botão "Avançar"); null se terminal.
 * aguardando → coletado é o "aceitar"; recusar é botão SEPARADO (não está aqui).
 */
export const NEXT_STATUS: Record<OrderStatus, OrderStatus | null> = {
  aguardando: 'coletado',
  coletado: 'em_processo',
  em_processo: 'pronto',
  pronto: 'saiu_entrega',
  saiu_entrega: 'entregue',
  entregue: null,
  recusado: null,
  cancelado: null,
}

/** Rótulo pt-BR de um status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  aguardando: 'Aguardando aceite',
  coletado: 'Coletado',
  em_processo: 'Em processo',
  pronto: 'Pronto',
  saiu_entrega: 'Saiu pra entrega',
  entregue: 'Entregue',
  recusado: 'Recusado',
  cancelado: 'Cancelado',
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** Formata uma data ISO (YYYY-MM-DD) em pt-BR sem deslocar fuso. */
export function formatDate(iso: string): string {
  if (!iso) return ''
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}
