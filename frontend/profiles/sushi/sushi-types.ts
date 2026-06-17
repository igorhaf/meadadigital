import type { SushiCategoryId } from './sushi-categories'

/** Item de cardápio (espelha SushiMenuItem do backend). */
export type MenuItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: SushiCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
}

/** Status de um pedido (espelha SushiOrderStatus). Ordem fixa. */
export type OrderStatus =
  | 'recebido'
  | 'preparo'
  | 'saiu_pra_entrega'
  | 'entregue'
  | 'cancelado'

/** Item de um pedido (snapshot de nome+preço). */
export type OrderItem = {
  id: string
  menuItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
}

/** Pedido (espelha SushiOrder). */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  subtotalCents: number
  deliveryFeeCents: number
  totalCents: number
  deliveryAddress: string
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { status: OrderStatus; label: string }[] = [
  { status: 'recebido', label: 'Recebido' },
  { status: 'preparo', label: 'Em preparo' },
  { status: 'saiu_pra_entrega', label: 'Saiu pra entrega' },
]

/** Próximo status no fluxo (para o botão "Avançar"); null se terminal/entregue. */
export const NEXT_STATUS: Record<OrderStatus, OrderStatus | null> = {
  recebido: 'preparo',
  preparo: 'saiu_pra_entrega',
  saiu_pra_entrega: 'entregue',
  entregue: null,
  cancelado: null,
}

/** Rótulo pt-BR de um status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  recebido: 'Recebido',
  preparo: 'Em preparo',
  saiu_pra_entrega: 'Saiu pra entrega',
  entregue: 'Entregue',
  cancelado: 'Cancelado',
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}
