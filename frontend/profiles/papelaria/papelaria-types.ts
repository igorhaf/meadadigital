import type { PapelariaCategoryId } from './papelaria-categories'
import type { PapelariaFulfillmentId } from './papelaria-fulfillment'

/**
 * Opção/modifier de um item de catálogo (espelha PapelariaCatalogOption do backend).
 * Agrupada por groupLabel (ex.: "Papel", "Acabamento", "Cor", "Tamanho"); priceDeltaCents soma ao
 * preço base.
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

/**
 * Item de catálogo (espelha PapelariaCatalogItem do backend) + suas opções.
 * ESCAPADA (8.15): madeToOrder (sob encomenda) + leadTimeDays (prazo próprio; null usa o default do
 * config) + specs (texto livre de especificações: gramatura, dimensões, técnica de impressão…).
 */
export type CatalogItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: PapelariaCategoryId
  madeToOrder: boolean
  leadTimeDays: number | null
  specs: string | null
  available: boolean
  createdAt: string
  updatedAt: string
  options: CatalogOption[]
}

/** Status de um pedido (espelha PapelariaOrderStatus). Ordem fixa. */
export type OrderStatus =
  | 'aguardando'
  | 'aceito'
  | 'arte_aprovacao'
  | 'em_producao'
  | 'pronto'
  | 'retirado'
  | 'saiu_entrega'
  | 'entregue'
  | 'recusado'
  | 'cancelado'

/** Forma de entrega (espelha PapelariaFulfillment). */
export type Fulfillment = PapelariaFulfillmentId

/** Opção escolhida num item de pedido (snapshot de label+delta no momento do pedido). */
export type OrderItemOption = {
  id: string
  catalogOptionId: string | null
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
}

/**
 * Item de um pedido (snapshot de nome+preço) + as opções escolhidas.
 * ESCAPADA (8.15): quantity (TIRAGEM — a quantidade impressa, escala o preço) + madeToOrder
 * (snapshot do sob-encomenda) + customText (texto da personalização: nomes dos noivos, data…).
 */
export type OrderItem = {
  id: string
  catalogItemId: string
  itemName: string
  quantity: number
  unitPriceCents: number
  madeToOrder: boolean
  customText: string | null
  options: OrderItemOption[]
}

/**
 * Pedido (espelha PapelariaOrder). rejectionReason preenchido só quando status = recusado.
 * ESCAPADA (8.15): fulfillment (retirada/entrega) + a data agendada de retirada/entrega
 * (pickupOrDeliveryDate) + deliveryPeriod (manha/tarde) + PROVA DE ARTE (artApproved + artUrl). O
 * lojista sobe a arte (artUrl) na etapa arte_aprovacao; só vai pra em_producao com artApproved =
 * true. deliveryAddress só em entrega.
 */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  fulfillment: Fulfillment
  subtotalCents: number
  deliveryFeeCents: number
  totalCents: number
  deliveryAddress: string | null
  notes: string | null
  rejectionReason: string | null
  pickupOrDeliveryDate: string | null
  deliveryPeriod: string | null
  artApproved: boolean
  artUrl: string | null
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
  depositCents: number | null
  depositPaid: boolean
  depositPaidAt: string | null
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { id: OrderStatus; label: string }[] = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'aceito', label: 'Aceito' },
  { id: 'arte_aprovacao', label: 'Aprovação de arte' },
  { id: 'em_producao', label: 'Em produção' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
]

/**
 * Próximo status no fluxo (botão "Avançar") em função da FORMA DE ENTREGA; null se terminal ou se a
 * transição depende de uma ação específica (aceito → arte/produção e arte_aprovacao → produção são
 * botões próprios da prova de arte, não o "Avançar" genérico).
 * O funil diverge em "pronto": retirada → retirado; entrega → saiu_entrega.
 */
export function nextStatus(status: OrderStatus, fulfillment: Fulfillment): OrderStatus | null {
  switch (status) {
    case 'em_producao':
      return 'pronto'
    case 'pronto':
      return fulfillment === 'retirada' ? 'retirado' : 'saiu_entrega'
    case 'saiu_entrega':
      return 'entregue'
    default:
      return null
  }
}

/** Rótulo pt-BR de um status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  aguardando: 'Aguardando aceite',
  aceito: 'Aceito',
  arte_aprovacao: 'Aprovação de arte',
  em_producao: 'Em produção',
  pronto: 'Pronto',
  retirado: 'Retirado',
  saiu_entrega: 'Saiu pra entrega',
  entregue: 'Entregue',
  recusado: 'Recusado',
  cancelado: 'Cancelado',
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}
