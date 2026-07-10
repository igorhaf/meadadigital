import type { OticaCategoryId } from './otica-categories'
import type { OticaExamStatusId } from './otica-exam-status'
import type { OticaOrderStatusId } from './otica-order-status'

/**
 * Tipos do perfil otica (loja de ótica, camada 8.12) — HÍBRIDO de DOIS fluxos:
 *   FLUXO A — agenda de exames de vista (clone do chassi clínico dental/dermatologia).
 *   FLUXO B — pedido de óculos sob receita (clone do chassi floricultura/comida) + receita + prazo.
 * Espelham as entidades do backend. ÓTICA NÃO tem paciente/cliente como entidade: o cliente é o
 * contato, e o exame guarda o nome do cliente como texto livre (customerName).
 */

// ─────────────────────────── Config (FUSED — ambos os fluxos) ───────────────────────────

/**
 * Config da ótica (espelha OticaConfig). opensAt/closesAt em "HH:MM:SS" (janela do exame).
 * examDurationMinutes = duração de cada exame; minOrderCents/leadTimeDaysDefault = pedido de óculos.
 */
export type Config = {
  companyId: string
  opensAt: string
  closesAt: string
  examDurationMinutes: number
  minOrderCents: number
  leadTimeDaysDefault: number
  examReminderEnabled: boolean
  pickupFollowupEnabled: boolean
  pickupFollowupDays: number
}

// ─────────────────────────── FLUXO A — exames de vista ───────────────────────────

/** Optometrista (espelha OticaProfessional). */
export type Professional = {
  id: string
  name: string
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Exame agendado (espelha OticaExamAppointment). startAt/endAt em ISO-8601 instant. */
export type ExamAppointment = {
  id: string
  professionalId: string
  professionalName: string
  customerName: string
  contactId: string | null
  conversationId: string | null
  durationMinutes: number
  startAt: string
  endAt: string
  status: OticaExamStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_slot (por profissional). */
export type ConflictDetail = {
  appointmentId: string
  customerName: string
  startAt: string
  endAt: string
}

// ─────────────────────────── FLUXO B — catálogo de óculos ───────────────────────────

/**
 * Opção/modifier de um item de catálogo (espelha OticaCatalogOption do backend).
 * Agrupada por groupLabel (ex.: "Tipo de lente", "Tratamento"); priceDeltaCents soma ao preço base.
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
 * Item de catálogo (espelha OticaCatalogItem do backend) + suas opções.
 * madeToOrder = sob encomenda (lente surfaçada) → tem leadTimeDays (prazo de produção em dias).
 */
export type CatalogItem = {
  id: string
  name: string
  description: string | null
  priceCents: number
  category: OticaCategoryId
  madeToOrder: boolean
  leadTimeDays: number | null
  available: boolean
  createdAt: string
  updatedAt: string
  options: CatalogOption[]
}

// ─────────────────────────── FLUXO B — pedidos de óculos ───────────────────────────

/** Opção escolhida num item de pedido (snapshot de label+delta no momento do pedido). */
export type OrderItemOption = {
  id: string
  catalogOptionId: string | null
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
}

/** Item de um pedido (snapshot de nome+preço+sob-encomenda) + as opções escolhidas. */
export type OrderItem = {
  id: string
  catalogItemId: string
  itemName: string
  qtd: number
  unitPriceCents: number
  madeToOrder: boolean
  options: OrderItemOption[]
}

/**
 * Pedido de óculos (espelha OticaOrder). rejectionReason preenchido só quando status = recusado.
 * Carrega a RECEITA (rx*): grau OD/OE (esférico/cilíndrico/eixo) + DP (distância pupilar).
 * prescriptionPending = o cliente ainda vai trazer a receita (a loja não monta sem ela).
 * readyDate = prazo de retirada (collect + maior leadTime dos itens sob encomenda); null se à pronta.
 */
export type Order = {
  id: string
  conversationId: string | null
  status: OticaOrderStatusId
  subtotalCents: number
  totalCents: number
  readyDate: string | null
  notes: string | null
  rejectionReason: string | null
  // Receita (prescrição):
  rxOdSpherical: string | null
  rxOdCylindrical: string | null
  rxOdAxis: string | null
  rxOeSpherical: string | null
  rxOeCylindrical: string | null
  rxOeAxis: string | null
  rxPd: string | null
  prescriptionPending: boolean
  createdAt: string
  statusUpdatedAt: string
  contactName: string | null
  contactPhone: string | null
  items: OrderItem[]
}

/** Colunas do Kanban (status em andamento) na ordem do fluxo. */
export const KANBAN_COLUMNS: { id: OticaOrderStatusId; label: string }[] = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_montagem', label: 'Em montagem' },
  { id: 'pronto', label: 'Pronto' },
]

/**
 * Próximo status no fluxo (botão "Avançar"); null se terminal.
 * aguardando → em_montagem é o "aceitar"; recusar é botão SEPARADO (não está aqui).
 */
export const NEXT_STATUS: Record<OticaOrderStatusId, OticaOrderStatusId | null> = {
  aguardando: 'em_montagem',
  em_montagem: 'pronto',
  pronto: 'retirado',
  retirado: null,
  recusado: null,
  cancelado: null,
}

/** Rótulo pt-BR de um status. */
export const STATUS_LABEL: Record<OticaOrderStatusId, string> = {
  aguardando: 'Aguardando aceite',
  em_montagem: 'Em montagem',
  pronto: 'Pronto',
  retirado: 'Retirado',
  recusado: 'Recusado',
  cancelado: 'Cancelado',
}

// ─────────────────────────── formatadores ───────────────────────────

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}
