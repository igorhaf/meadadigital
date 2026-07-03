import { apiFetch } from '@/lib/api/client'
import type { TravelProposalStatusId } from '@/profiles/viagens/travel-proposal-status'
import type {
  ItemCategoryId,
  ItineraryDay,
  Proposal,
  ProposalItem,
} from '@/profiles/viagens/viagens-types'

type ProposalPage = { items: Proposal[]; total: number; page: number; pageSize: number }

export type OpenProposalInput = {
  contactId?: string | null
  customerName?: string | null
  consultantId?: string | null
  destination?: string | null
  startDate?: string | null // yyyy-MM-dd
  endDate?: string | null // yyyy-MM-dd
  numTravelers?: number | null
  travelStyle?: string | null
  briefing?: string | null
  notes?: string | null
}

export type UpdateProposalInput = {
  consultantId?: string | null
  clearConsultant?: boolean
  destination?: string | null
  startDate?: string | null
  clearStartDate?: boolean
  endDate?: string | null
  clearEndDate?: boolean
  numTravelers?: number | null
  clearNumTravelers?: boolean
  travelStyle?: string | null
  briefing?: string | null
  notes?: string | null
}

export type AddItemInput = {
  category: ItemCategoryId
  description: string
  quantity: number
  unitPriceCents: number
}

export type UpdateItemInput = Partial<AddItemInput>

export type AddItineraryDayInput = {
  dayDate?: string | null // yyyy-MM-dd
  title: string
  description?: string | null
}

export type UpdateItineraryDayInput = {
  dayDate?: string | null
  clearDayDate?: boolean
  title?: string
  description?: string | null
  clearDescription?: boolean
}

export function listProposals(
  opts: {
    status?: string
    consultantId?: string
    contactId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<ProposalPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.consultantId) p.set('consultantId', opts.consultantId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ProposalPage>(`/api/viagens/proposals${qs ? `?${qs}` : ''}`)
}

export function getProposal(id: string): Promise<Proposal> {
  return apiFetch<Proposal>(`/api/viagens/proposals/${id}`)
}

export function openProposal(input: OpenProposalInput): Promise<Proposal> {
  return apiFetch<Proposal>('/api/viagens/proposals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProposal(id: string, input: UpdateProposalInput): Promise<Proposal> {
  return apiFetch<Proposal>(`/api/viagens/proposals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

// ---- Itens de COTAÇÃO (entram no total) ----

export function addItem(proposalId: string, input: AddItemInput): Promise<ProposalItem> {
  return apiFetch<ProposalItem>(`/api/viagens/proposals/${proposalId}/items`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateItem(
  proposalId: string,
  itemId: string,
  input: UpdateItemInput,
): Promise<ProposalItem> {
  return apiFetch<ProposalItem>(`/api/viagens/proposals/${proposalId}/items/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteItem(proposalId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/viagens/proposals/${proposalId}/items/${itemId}`, {
    method: 'DELETE',
  })
}

// ---- Dias do ITINERÁRIO (a escapada — NÃO entram no total, ordenados por dayNumber) ----

export function addItineraryDay(
  proposalId: string,
  input: AddItineraryDayInput,
): Promise<ItineraryDay> {
  return apiFetch<ItineraryDay>(`/api/viagens/proposals/${proposalId}/itinerary`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateItineraryDay(
  proposalId: string,
  dayId: string,
  input: UpdateItineraryDayInput,
): Promise<ItineraryDay> {
  return apiFetch<ItineraryDay>(`/api/viagens/proposals/${proposalId}/itinerary/${dayId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteItineraryDay(proposalId: string, dayId: string): Promise<void> {
  return apiFetch<void>(`/api/viagens/proposals/${proposalId}/itinerary/${dayId}`, {
    method: 'DELETE',
  })
}

/** Reordena os dias do itinerário: envia a lista de ids na ordem desejada (recomputa dayNumber 1..N). */
export function reorderItineraryDays(
  proposalId: string,
  orderedIds: string[],
): Promise<{ itinerary: ItineraryDay[] }> {
  return apiFetch<{ itinerary: ItineraryDay[] }>(
    `/api/viagens/proposals/${proposalId}/itinerary/reorder`,
    {
      method: 'PATCH',
      body: JSON.stringify({ orderedIds }),
    },
  )
}

// ---- Status ----

export function updateProposalStatus(
  id: string,
  newStatus: TravelProposalStatusId,
): Promise<Proposal> {
  return apiFetch<Proposal>(`/api/viagens/proposals/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
