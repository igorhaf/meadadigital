import type { PadariaCategoryId } from './padaria-categories'
import type { PadariaFulfillmentId } from './padaria-fulfillment'

/**
 * Opção/modifier de um item de cardápio (ESCAPADA — espelha PadariaMenuOption do backend).
 * Agrupada por groupLabel (ex.: "Sabor", "Recheio", "Tamanho"); priceDeltaCents soma ao preço base.
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

/**
 * Item de cardápio (espelha PadariaMenuItem do backend) + suas opções.
 * ESCAPADA (8.8): madeToOrder (sob encomenda) + leadTimeDays (prazo próprio; null usa o default do
 * config) + allergens (texto livre de alérgenos).
 */
export type MenuItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: PadariaCategoryId
  madeToOrder: boolean
  leadTimeDays: number | null
  allergens: string | null
  available: boolean
  createdAt: string
  updatedAt: string
  options: MenuOption[]
}

/** Status de um pedido (espelha PadariaOrderStatus). Ordem fixa. */
export type OrderStatus =
  | 'aguardando'
  | 'em_preparo'
  | 'pronto'
  | 'retirado'
  | 'saiu_entrega'
  | 'entregue'
  | 'recusado'
  | 'cancelado'

/** Forma de entrega (espelha PadariaFulfillment). */
export type Fulfillment = PadariaFulfillmentId

/** Opção escolhida num item de pedido (snapshot de label+delta no momento do pedido). */
export type OrderItemOption = {
  id: string
  menuOptionId: string | null
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
}

/**
 * Item de um pedido (snapshot de nome+preço) + as opções escolhidas.
 * ESCAPADA (8.8): madeToOrder (snapshot do sob-encomenda) + cakeMessage (texto da placa do bolo,
 * só faz sentido em bolos/tortas).
 */
export type OrderItem = {
  id: string
  menuItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
  madeToOrder: boolean
  cakeMessage: string | null
  options: OrderItemOption[]
}

/**
 * Pedido (espelha PadariaOrder). rejectionReason preenchido só quando status = recusado.
 * ESCAPADA (8.8): fulfillment (retirada/entrega) + a data agendada de retirada/entrega
 * (pickupOrDeliveryDate) + deliveryPeriod (manha/tarde). deliveryAddress só em entrega.
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
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
]

/**
 * Próximo status no fluxo (botão "Avançar") em função da FORMA DE ENTREGA; null se terminal.
 * O funil diverge em "pronto": retirada → retirado; entrega → saiu_entrega.
 * aguardando → em_preparo é o "aceitar"; recusar é botão SEPARADO.
 */
export function nextStatus(status: OrderStatus, fulfillment: Fulfillment): OrderStatus | null {
  switch (status) {
    case 'aguardando':
      return 'em_preparo'
    case 'em_preparo':
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
  em_preparo: 'Em preparo',
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
