import type { AtelieProjectTypeId } from './atelie-project-type'
import type { AtelieProposalStatusId } from './atelie-proposal-status'

/** Artesão/responsável do tenant atelie (espelha AtelieArtisan). */
export type AtelieArtisan = {
  id: string
  companyId: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do tenant atelie (espelha AtelieConfig). Nome do ateliê + notas + lembrete de prova (SEM horário). */
export type AtelieConfig = {
  businessName: string | null
  notes: string | null
  fittingReminderEnabled: boolean
  postDeliveryEnabled: boolean
  reviewLink: string | null
  reactivationEnabled: boolean
  reactivationDays: number
}

/** Item de ORÇAMENTO de uma proposta (espelha AtelieProposalItem). lineTotalCents materializado. */
export type AtelieProposalItem = {
  id: string
  proposalId: string
  description: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
}

/** Status possíveis de uma prova/ajuste (sub-entidade — sem paridade, lista de 2 itens). */
export const FITTING_STATUSES = [
  { id: 'pendente', label: 'Pendente' },
  { id: 'realizada', label: 'Realizada' },
] as const

export type FittingStatusId = (typeof FITTING_STATUSES)[number]['id']

/** Etapa de PROVA/AJUSTE (espelha AtelieFitting). A ESCAPADA da SM: ordenada por position. */
export type AtelieFitting = {
  id: string
  proposalId: string
  title: string
  description: string | null
  dueDate: string | null
  status: FittingStatusId
  position: number
  completedAt: string | null
  confirmedAt: string | null
  confirmedDueDate: string | null
}

/** Proposta de ateliê (espelha AtelieProposal). totalCents materializado. items + fittings no detalhe. */
export type AtelieProposal = {
  id: string
  companyId: string
  contactId: string | null
  artisanId: string | null
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  projectType: AtelieProjectTypeId
  occasion: string | null
  briefing: string | null
  estimatedDate: string | null
  totalCents: number
  discountCents: number
  couponId: string | null
  couponCodeSnapshot: string | null
  status: AtelieProposalStatusId
  notes: string | null
  depositCents: number | null
  depositPaid: boolean
  depositPaidAt: string | null
  openedAt: string
  closedAt: string | null
  statusUpdatedAt: string
  items: AtelieProposalItem[]
  fittings: AtelieFitting[]
}

/** Item do catálogo de materiais/técnicas (onda 2, backlog #15 — espelha AtelieCatalogItem). */
export type AtelieCatalogItem = {
  id: string
  companyId: string
  name: string
  category: string | null
  unitPriceCents: number
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Cupom de desconto (onda 2, backlog #13 — espelha AtelieCoupon; clone do motor adega). */
export type AtelieCoupon = {
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

/** Medida do cliente/contato (onda 2, backlog #9 — espelha AtelieMeasurement). */
export type AtelieMeasurement = {
  id: string
  companyId: string
  contactId: string
  label: string
  value: string
  createdAt: string
  updatedAt: string
}

/** Linha agregada do relatório de faturamento (onda 2, backlog #14). */
export type AtelieReportRow = {
  month?: string
  projectType?: string
  artisanName?: string | null
  count: number
  totalCents: number
}

export type AtelieReportSummary = {
  months: number
  totalCount: number
  totalCents: number
  byMonth: AtelieReportRow[]
  byType: AtelieReportRow[]
  byArtisan: AtelieReportRow[]
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number | null | undefined): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('pt-BR')
}

/**
 * Alerta de atraso da entrega (onda backlog #12): prazo prometido (estimatedDate) já passou e a
 * proposta ainda está viva (não-terminal). Comparação por string yyyy-MM-dd no fuso LOCAL do
 * navegador (toLocaleDateString en-CA) — evita o off-by-one do toISOString/UTC à noite.
 */
export function isDeliveryOverdue(
  estimatedDate: string | null,
  status: AtelieProposalStatusId,
): boolean {
  if (!estimatedDate) return false
  if (status === 'realizada' || status === 'recusada' || status === 'cancelada') return false
  const today = new Date().toLocaleDateString('en-CA')
  return estimatedDate < today
}
