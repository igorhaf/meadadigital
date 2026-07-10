import type { LasCategoryId } from './las-categories'

/**
 * Tipos do perfil las (loja de lãs / novelos / tricô-crochê — varejo, camada 8.23).
 *
 * ESCAPADA da camada (vs lingerie): o eixo de variação é COR × LOTE DE TINGIMENTO (dye lot) em vez
 * de TAMANHO × COR. O dye lot é o lote de tingimento — novelos do MESMO lote têm o MESMO tom; lotes
 * diferentes podem ter pequenas variações de cor. Por isso o pedido carrega o flag
 * sameLotGuaranteed: quando true, a loja garante que todos os novelos saem do mesmo lote (mesmo tom).
 * Tanto cor quanto lote são TEXTO LIVRE (não enum). O cliente pede uma variante específica; o item
 * do pedido referencia a variante e congela name/color/dyeLot/preço.
 */

/**
 * Variante de um produto (espelha LasVariant do backend): combinação cor × lote de tingimento com
 * SKU, preço próprio (priceCents null = herda basePriceCents do produto) e estoque (stockQty).
 */
export type Variant = {
  id: string
  productId: string
  color: string
  dyeLot: string
  sku: string | null
  priceCents: number | null
  stockQty: number
  available: boolean
}

/** Produto de catálogo (espelha LasProduct do backend) + suas variantes. */
export type Product = {
  id: string
  name: string
  description: string | null
  basePriceCents: number
  category: LasCategoryId
  available: boolean
  createdAt: string
  updatedAt: string
  variants: Variant[]
}

/** Status de um pedido (espelha LasOrderStatus). Ordem fixa. */
export type OrderStatus =
  'aguardando' | 'separando' | 'enviado' | 'entregue' | 'recusado' | 'cancelado'

/** Forma de entrega de um pedido (espelha LasFulfillment). */
export type Fulfillment = 'entrega' | 'retirada'

/** Item de um pedido (snapshot de produto/cor/lote/preço da variante no momento do pedido). */
export type OrderItem = {
  id: string
  variantId: string
  productName: string
  color: string
  dyeLot: string
  qtd: number
  unitPriceCents: number
}

/**
 * Pedido (espelha LasOrder). rejectionReason preenchido só quando status = recusado.
 * fulfillment indica entrega (com deliveryAddress) ou retirada na loja.
 * sameLotGuaranteed = true quando a loja garante que todos os novelos saem do mesmo lote de
 * tingimento (mesmo tom).
 */
/** Cupom de desconto gerido pelo tenant (espelha LasCoupon do backend). */
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

/** Referência de rendimento (peça × fio → novelos estimados) da calculadora da IA. */
export type YieldReference = {
  id: string
  pieceType: string
  yarnSpec: string | null
  skeins: number
  notes: string | null
  active: boolean
}

export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
  fulfillment: Fulfillment
  sameLotGuaranteed: boolean
  subtotalCents: number
  discountCents: number
  deliveryFeeCents: number
  totalCents: number
  couponCode: string | null
  deliveryAddress: string | null
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
