/**
 * Tipos do perfil sushi (camada 7.1, reworkado). Categorias, status de pedido, cupons e fidelidade
 * agora são geridos pelo tenant via API (não mais hardcoded). Os tipos abaixo espelham o contrato
 * REST de /api/sushi/*.
 */

/** Categoria de cardápio gerida pelo tenant (espelha SushiCategory do backend). */
export type Category = {
  id: string
  name: string
  sortOrder: number
  active: boolean
}

/** Status de pedido gerido pelo tenant, com a notificação de WhatsApp ao entrar nele. */
export type OrderStatusDef = {
  id: string
  name: string
  sortOrder: number
  isInitial: boolean
  isTerminal: boolean
  notifyEnabled: boolean
  notifyText: string | null
  color: string | null
}

/** Cupom de desconto gerido pelo tenant. */
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

/** Configuração de fidelidade (a cada N pedidos, o próximo ganha um desconto). */
export type LoyaltyConfig = {
  enabled: boolean
  thresholdOrders: number
  rewardKind: 'percent' | 'fixed'
  rewardValue: number
}

/** Item de cardápio (espelha SushiMenuItem do backend). category = uuid da categoria (ou null). */
export type MenuItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: string | null
  available: boolean
  createdAt: string
  updatedAt: string
}

/** Item de um pedido (snapshot de nome+preço). */
export type OrderItem = {
  id: string
  menuItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
}

/** Pedido (espelha SushiOrder). status = uuid do status; statusName = rótulo resolvido. */
export type Order = {
  id: string
  conversationId: string
  status: string
  statusName: string
  subtotalCents: number
  deliveryFeeCents: number
  discountCents: number
  totalCents: number
  couponCode: string | null
  loyaltyApplied: boolean
  fulfillment: 'entrega' | 'retirada'
  scheduledDate: string | null
  scheduledPeriod: string | null
  deliveryAddress: string | null
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}
