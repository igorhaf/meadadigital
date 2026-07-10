import type { LingerieCategoryId } from './lingerie-categories'

/**
 * Tipos do perfil lingerie (moda íntima / varejo, camada 8.21).
 *
 * ESCAPADA da camada (vs adega): a camada de "opções/modifiers" do cardápio é substituída por
 * uma GRADE DE VARIANTES (tamanho × cor) com ESTOQUE por variante. O cliente pede uma variante
 * específica; o item do pedido referencia a variante e congela name/size/color/preço.
 */

/**
 * Variante de um produto (espelha LingerieVariant do backend): combinação tamanho × cor com
 * SKU, preço próprio (priceCents null = herda basePriceCents do produto) e estoque (stockQty).
 */
export type Variant = {
  id: string
  productId: string
  size: string
  color: string
  sku: string | null
  priceCents: number | null
  stockQty: number
  available: boolean
}

/** Produto de catálogo (espelha LingerieProduct do backend) + suas variantes. */
export type Product = {
  id: string
  name: string
  description: string | null
  basePriceCents: number
  category: LingerieCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
  variants: Variant[]
}

/** Status de um pedido (espelha LingerieOrderStatus). Ordem fixa. */
export type OrderStatus =
  'aguardando' | 'separando' | 'enviado' | 'entregue' | 'recusado' | 'cancelado'

/** Forma de entrega de um pedido (espelha LingerieFulfillment). */
export type Fulfillment = 'entrega' | 'retirada'

/** Item de um pedido (snapshot de produto/tamanho/cor/preço da variante no momento do pedido). */
export type OrderItem = {
  id: string
  variantId: string
  productName: string
  size: string
  color: string
  qtd: number
  unitPriceCents: number
}

/**
 * Pedido (espelha LingerieOrder). rejectionReason preenchido só quando status = recusado.
 * fulfillment indica entrega (com deliveryAddress) ou retirada na loja.
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
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
  discountCents: number
  couponCode: string | null
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { id: OrderStatus; label: string }[] = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'separando', label: 'Separando' },
  { id: 'enviado', label: 'Enviado' },
]

/**
 * Próximo status no fluxo (botão "Avançar"); null se terminal.
 * aguardando → separando é o "aceitar"; recusar é botão SEPARADO (não está aqui).
 */
export const NEXT_STATUS: Record<OrderStatus, OrderStatus | null> = {
  aguardando: 'separando',
  separando: 'enviado',
  enviado: 'entregue',
  entregue: null,
  recusado: null,
  cancelado: null,
}

/** Rótulo pt-BR de um status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  aguardando: 'Aguardando aceite',
  separando: 'Separando',
  enviado: 'Enviado',
  entregue: 'Entregue',
  recusado: 'Recusado',
  cancelado: 'Cancelado',
}

/** Rótulo pt-BR da forma de entrega. */
export const FULFILLMENT_LABEL: Record<Fulfillment, string> = {
  entrega: 'Entrega',
  retirada: 'Retirada',
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** Cupom de desconto gerido pelo tenant (onda 1 — motor unificado). */
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
