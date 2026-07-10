import { apiFetch } from '@/lib/api/client'
import type { EventProposalStatusId } from '@/profiles/eventos/event-proposal-status'
import type {
  EventProposal,
  EventProposalItem,
  EventTimelineItem,
} from '@/profiles/eventos/eventos-types'

type ProposalPage = { items: EventProposal[]; total: number; page: number; pageSize: number }

export type OpenProposalInput = {
  contactId?: string | null
  customerName?: string | null
  plannerId?: string | null
  eventType?: string | null
  eventDate?: string | null // yyyy-MM-dd
  guestCount?: number | null
  briefing?: string | null
  notes?: string | null
}

export type UpdateProposalInput = {
  plannerId?: string | null
  clearPlanner?: boolean
  eventType?: string | null
  eventDate?: string | null
  clearEventDate?: boolean
  guestCount?: number | null
  clearGuestCount?: boolean
  briefing?: string | null
  notes?: string | null
}

export type AddItemInput = {
  description: string
  quantity: number
  unitPriceCents: number
}

export type UpdateItemInput = Partial<AddItemInput>

export type AddTimelineInput = {
  startTime: string // HH:MM
  title: string
  description?: string | null
}

export type UpdateTimelineInput = {
  startTime?: string
  title?: string
  description?: string | null
  clearDescription?: boolean
}

export function listProposals(
  opts: {
    status?: string
    plannerId?: string
    contactId?: string
    eventDateFrom?: string
    eventDateTo?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<ProposalPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.plannerId) p.set('plannerId', opts.plannerId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.eventDateFrom) p.set('eventDateFrom', opts.eventDateFrom)
  if (opts.eventDateTo) p.set('eventDateTo', opts.eventDateTo)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ProposalPage>(`/api/eventos/proposals${qs ? `?${qs}` : ''}`)
}

export function getProposal(id: string): Promise<EventProposal> {
  return apiFetch<EventProposal>(`/api/eventos/proposals/${id}`)
}

export function openProposal(input: OpenProposalInput): Promise<EventProposal> {
  return apiFetch<EventProposal>('/api/eventos/proposals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProposal(id: string, input: UpdateProposalInput): Promise<EventProposal> {
  return apiFetch<EventProposal>(`/api/eventos/proposals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

// ---- Itens de ORÇAMENTO (entram no total) ----

export function addItem(proposalId: string, input: AddItemInput): Promise<EventProposalItem> {
  return apiFetch<EventProposalItem>(`/api/eventos/proposals/${proposalId}/items`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateItem(
  proposalId: string,
  itemId: string,
  input: UpdateItemInput,
): Promise<EventProposalItem> {
  return apiFetch<EventProposalItem>(`/api/eventos/proposals/${proposalId}/items/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteItem(proposalId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/eventos/proposals/${proposalId}/items/${itemId}`, {
    method: 'DELETE',
  })
}

// ---- Marcos de CRONOGRAMA (a escapada — NÃO entram no total) ----

export function addTimelineItem(
  proposalId: string,
  input: AddTimelineInput,
): Promise<EventTimelineItem> {
  return apiFetch<EventTimelineItem>(`/api/eventos/proposals/${proposalId}/timeline`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateTimelineItem(
  proposalId: string,
  itemId: string,
  input: UpdateTimelineInput,
): Promise<EventTimelineItem> {
  return apiFetch<EventTimelineItem>(`/api/eventos/proposals/${proposalId}/timeline/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteTimelineItem(proposalId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/eventos/proposals/${proposalId}/timeline/${itemId}`, {
    method: 'DELETE',
  })
}

// ---- Status ----

export function updateProposalStatus(
  id: string,
  newStatus: EventProposalStatusId,
): Promise<EventProposal> {
  return apiFetch<EventProposal>(`/api/eventos/proposals/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
