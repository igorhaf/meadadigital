import type { TravelProposalStatusId } from './travel-proposal-status'

/** Consultor de viagens do tenant viagens (espelha TravelConsultant). Catálogo simples — sem agenda. */
export type Consultant = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do tenant viagens (espelha TravelConfig). Nome da agência + notas (SEM horário). */
export type Config = {
  companyId: string
  businessName: string | null
  notes: string | null
}

/**
 * Categorias dos itens de COTAÇÃO (espelha TravelItemCategory). Campo do item, não parity.
 * aereo · hospedagem · traslado · passeio · outro.
 */
export const ITEM_CATEGORIES = [
  { id: 'aereo', label: 'Aéreo' },
  { id: 'hospedagem', label: 'Hospedagem' },
  { id: 'traslado', label: 'Traslado' },
  { id: 'passeio', label: 'Passeio' },
  { id: 'outro', label: 'Outro' },
] as const

export type ItemCategory = (typeof ITEM_CATEGORIES)[number]
export type ItemCategoryId = ItemCategory['id']

/** Rótulo pt-BR de uma categoria de item (fallback: o próprio id). */
export function categoryLabel(id: string): string {
  return ITEM_CATEGORIES.find((c) => c.id === id)?.label ?? id
}

/** Item de COTAÇÃO de uma proposta (espelha TravelProposalItem). lineTotalCents materializado. ENTRA no total. */
export type ProposalItem = {
  id: string
  proposalId: string
  category: ItemCategoryId
  description: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
  createdAt: string
  updatedAt: string
}

/**
 * Dia do ITINERÁRIO multi-dia (espelha TravelItineraryDay). A ESCAPADA da SM: NÃO entra no total —
 * ordenado por dayNumber. Sem status (≠ as provas do ateliê).
 */
export type ItineraryDay = {
  id: string
  proposalId: string
  dayNumber: number
  dayDate: string | null
  title: string
  description: string | null
  createdAt: string
  updatedAt: string
}

/** Proposta de viagem (espelha TravelProposal). totalCents materializado. items + itinerary no detalhe. */
export type Proposal = {
  id: string
  contactId: string | null
  consultantId: string | null
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  consultantName: string | null
  destination: string | null
  startDate: string | null
  endDate: string | null
  numTravelers: number | null
  travelStyle: string | null
  briefing: string | null
  totalCents: number
  status: TravelProposalStatusId
  notes: string | null
  openedAt: string
  closedAt: string | null
  statusUpdatedAt: string
  items: ProposalItem[]
  itinerary: ItineraryDay[]
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number | null | undefined): string {
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

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('pt-BR')
}
