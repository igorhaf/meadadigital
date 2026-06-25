import { apiFetch } from '@/lib/api/client'
import type { AtelieProposalStatusId } from '@/profiles/atelie/atelie-proposal-status'
import type { AtelieProjectTypeId } from '@/profiles/atelie/atelie-project-type'
import type {
  AtelieProposal,
  AtelieProposalItem,
  AtelieFitting,
  FittingStatusId,
} from '@/profiles/atelie/atelie-types'

type ProposalPage = { items: AtelieProposal[]; total: number; page: number; pageSize: number }

export type OpenProposalInput = {
  contactId?: string | null
  customerName?: string | null
  artisanId?: string | null
  projectType?: AtelieProjectTypeId | null
  occasion?: string | null
  estimatedDate?: string | null // yyyy-MM-dd
  briefing?: string | null
  notes?: string | null
}

export type AddItemInput = {
  description: string
  quantity: number
  unitPriceCents: number
}

export type UpdateItemInput = Partial<AddItemInput>

export type AddFittingInput = {
  title: string
  description?: string | null
  dueDate?: string | null // yyyy-MM-dd
}

export type UpdateFittingInput = {
  title?: string
  description?: string | null
  clearDescription?: boolean
  dueDate?: string | null
  clearDueDate?: boolean
}

export function listProposals(
  opts: { status?: string; artisanId?: string; contactId?: string; page?: number; pageSize?: number } = {},
): Promise<ProposalPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.artisanId) p.set('artisanId', opts.artisanId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ProposalPage>(`/api/atelie/proposals${qs ? `?${qs}` : ''}`)
}

export function getProposal(id: string): Promise<AtelieProposal> {
  return apiFetch<AtelieProposal>(`/api/atelie/proposals/${id}`)
}

export function openProposal(input: OpenProposalInput): Promise<AtelieProposal> {
  return apiFetch<AtelieProposal>('/api/atelie/proposals', { method: 'POST', body: JSON.stringify(input) })
}

// ---- Itens de ORÇAMENTO (entram no total) ----

export function addItem(proposalId: string, input: AddItemInput): Promise<AtelieProposalItem> {
  return apiFetch<AtelieProposalItem>(`/api/atelie/proposals/${proposalId}/items`, {
    method: 'POST', body: JSON.stringify(input),
  })
}

export function updateItem(proposalId: string, itemId: string, input: UpdateItemInput): Promise<AtelieProposalItem> {
  return apiFetch<AtelieProposalItem>(`/api/atelie/proposals/${proposalId}/items/${itemId}`, {
    method: 'PATCH', body: JSON.stringify(input),
  })
}

export function deleteItem(proposalId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/atelie/proposals/${proposalId}/items/${itemId}`, { method: 'DELETE' })
}

// ---- Provas/ajustes (a escapada — NÃO entram no total, ordenadas por position) ----

export function addFitting(proposalId: string, input: AddFittingInput): Promise<AtelieFitting> {
  return apiFetch<AtelieFitting>(`/api/atelie/proposals/${proposalId}/fittings`, {
    method: 'POST', body: JSON.stringify(input),
  })
}

export function updateFitting(proposalId: string, fittingId: string, input: UpdateFittingInput): Promise<AtelieFitting> {
  return apiFetch<AtelieFitting>(`/api/atelie/proposals/${proposalId}/fittings/${fittingId}`, {
    method: 'PATCH', body: JSON.stringify(input),
  })
}

export function deleteFitting(proposalId: string, fittingId: string): Promise<void> {
  return apiFetch<void>(`/api/atelie/proposals/${proposalId}/fittings/${fittingId}`, { method: 'DELETE' })
}

export function reorderFittings(proposalId: string, orderedIds: string[]): Promise<{ items: AtelieFitting[] }> {
  return apiFetch<{ items: AtelieFitting[] }>(`/api/atelie/proposals/${proposalId}/fittings/reorder`, {
    method: 'PATCH', body: JSON.stringify({ orderedIds }),
  })
}

export function transitionFitting(proposalId: string, fittingId: string, status: FittingStatusId): Promise<AtelieFitting> {
  return apiFetch<AtelieFitting>(`/api/atelie/proposals/${proposalId}/fittings/${fittingId}/status`, {
    method: 'PATCH', body: JSON.stringify({ status }),
  })
}

// ---- Status ----

export function updateProposalStatus(id: string, newStatus: AtelieProposalStatusId): Promise<AtelieProposal> {
  return apiFetch<AtelieProposal>(`/api/atelie/proposals/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}
