import type { WeddingProposalStatusId } from './wedding-proposal-status'

/** Assessor(a) de casamento do tenant casamento (espelha WeddingPlanner). */
export type WeddingPlanner = {
  id: string
  companyId: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do tenant casamento (espelha WeddingConfig). Nome do negócio + notas (SEM horário). */
export type WeddingConfig = {
  businessName: string | null
  notes: string | null
}

/** Item de ORÇAMENTO de uma proposta (espelha WeddingProposalItem). lineTotalCents materializado. */
export type WeddingProposalItem = {
  id: string
  proposalId: string
  description: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
}

/** Marco de CRONOGRAMA do dia do casamento (espelha WeddingTimelineItem). startTime em "HH:MM[:SS]".
 * NÃO entra no total — ordenado por start_time. */
export type WeddingTimelineItem = {
  id: string
  proposalId: string
  startTime: string
  title: string
  description: string | null
}

/** Tarefa do CHECKLIST pré-casamento (espelha WeddingChecklistTask). A ESCAPADA da SM: ordenada por
 * due_date (NULLS LAST). NÃO entra no total — marca/desmarca via toggle done. */
export type WeddingChecklistTask = {
  id: string
  proposalId: string
  title: string
  description: string | null
  dueDate: string | null
  done: boolean
  doneAt: string | null
}

/** Proposta de casamento (espelha WeddingProposal). totalCents materializado. items + timeline +
 * checklist no detalhe. */
export type WeddingProposal = {
  id: string
  companyId: string
  contactId: string | null
  plannerId: string | null
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  weddingStyle: string | null
  weddingDate: string | null
  guestCount: number | null
  briefing: string | null
  totalCents: number
  status: WeddingProposalStatusId
  notes: string | null
  openedAt: string
  closedAt: string | null
  statusUpdatedAt: string
  items: WeddingProposalItem[]
  timeline: WeddingTimelineItem[]
  checklist: WeddingChecklistTask[]
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

/** Formata "HH:MM:SS" → "HH:MM" (display do cronograma). */
export function formatTime(t: string | null | undefined): string {
  if (!t) return '—'
  return t.slice(0, 5)
}
