import type { SuplementosCategoryId } from './suplementos-categories'

/**
 * Tipos do perfil suplementos (loja de saúde / nutrição esportiva / varejo, camada 8.24).
 *
 * CLONE do lingerie: a camada de "opções/modifiers" do cardápio é substituída por uma GRADE DE
 * VARIANTES com ESTOQUE por variante. AQUI o eixo é SABOR × TAMANHO (peso/tamanho), não tamanho ×
 * cor. O cliente pede uma variante específica; o item do pedido referencia a variante e congela
 * name/label/preço. SÓ ENTREGA (não há retirada nesta camada).
 */

/**
 * Variante de um produto (espelha SuplementosVariant do backend): combinação sabor × tamanho com
 * SKU, preço próprio, estoque (stockQuantity) e validade opcional (expiryDate). flavor é opcional
 * (ex.: acessórios e cápsulas não têm sabor); sizeLabel é o peso/tamanho (ex.: "900g", "60 caps").
 */
export type Variant = {
  id: string
  productId: string
  flavor: string | null
  sizeLabel: string
  sku: string | null
  priceCents: number
  stockQuantity: number
  expiryDate: string | null
  active: boolean
}

/** Produto de catálogo (espelha SuplementosProduct do backend) + suas variantes. */
export type Product = {
  id: string
  name: string
  brand: string | null
  description: string | null
  category: SuplementosCategoryId
  active: boolean
  createdAt: string
  updatedAt: string
  variants: Variant[]
}

/** Status de um pedido (espelha SuplementosOrderStatus). Ordem fixa. */
export type OrderStatus =
  'aguardando' | 'em_preparo' | 'saiu_entrega' | 'entregue' | 'recusado' | 'cancelado'

/** Item de um pedido (snapshot de produto/variante/preço no momento do pedido). */
export type OrderItem = {
  id: string
  productId: string
  variantId: string
  productName: string
  variantLabel: string
  qtd: number
  unitPriceCents: number
}

/**
 * Pedido (espelha SuplementosOrder). rejectionReason preenchido só quando status = recusado.
 * SÓ ENTREGA: deliveryAddress sempre presente (não há retirada).
 */
export type Order = {
  id: string
  conversationId: string
  status: OrderStatus
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
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { id: OrderStatus; label: string }[] = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'saiu_entrega', label: 'Saiu para entrega' },
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
  saiu_entrega: 'Saiu para entrega',
  entregue: 'Entregue',
  recusado: 'Recusado',
  cancelado: 'Cancelado',
}

/** Rótulo de uma variante: "Chocolate · 900g" (omite o sabor quando ausente). */
export function variantLabel(v: Variant): string {
  return v.flavor ? `${v.flavor} · ${v.sizeLabel}` : v.sizeLabel
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}
