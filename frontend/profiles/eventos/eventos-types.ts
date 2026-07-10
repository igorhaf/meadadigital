import type { EventProposalStatusId } from './event-proposal-status'

/** Cerimonialista/responsável do tenant eventos (espelha EventPlanner). */
export type EventPlanner = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do tenant eventos (espelha EventConfig). Nome do espaço + notas (SEM horário). */
export type EventConfig = {
  companyId: string
  businessName: string | null
  notes: string | null
  autoCompleteEnabled: boolean
  postEventEnabled: boolean
  reviewLink: string | null
  followUpEnabled: boolean
  followUpDays: number
}

/** Pacote/adicional do catálogo do buffet (espelha EventPackage — onda 1, backlog #2). */
export type EventPackage = {
  id: string
  name: string
  kind: 'pacote' | 'adicional'
  description: string | null
  priceCents: number
  suggestible: boolean
  active: boolean
}

/** Item de ORÇAMENTO de uma proposta (espelha EventProposalItem). lineTotalCents materializado. */
export type EventProposalItem = {
  id: string
  proposalId: string
  description: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
  createdAt: string
  updatedAt: string
}

/** Marco de CRONOGRAMA do dia do evento (espelha EventTimelineItem). startTime em "HH:MM:SS".
 * A ESCAPADA da SM: NÃO entra no total — ordenado por start_time. */
export type EventTimelineItem = {
  id: string
  proposalId: string
  startTime: string
  title: string
  description: string | null
  createdAt: string
  updatedAt: string
}

/** Proposta de evento (espelha EventProposal). totalCents materializado. items + timeline no detalhe. */
export type EventProposal = {
  id: string
  contactId: string | null
  plannerId: string | null
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  plannerName: string | null
  eventType: string | null
  eventDate: string | null
  guestCount: number | null
  briefing: string | null
  totalCents: number
  status: EventProposalStatusId
  notes: string | null
  openedAt: string
  closedAt: string | null
  statusUpdatedAt: string
  items: EventProposalItem[]
  timeline: EventTimelineItem[]
}

/** Formata centavos em R$ pt-BR. */
export function formatPrice(cents: number | null | undefined): string {
  if (cents == null) return '—'
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

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}

/** Formata "HH:MM:SS" → "HH:MM" (display do cronograma). */
export function formatTime(t: string | null | undefined): string {
  if (!t) return '—'
  return t.slice(0, 5)
}
