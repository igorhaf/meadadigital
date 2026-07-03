import { apiFetch } from '@/lib/api/client'
import type {
  WeddingChecklistTask,
  WeddingProposal,
  WeddingProposalItem,
  WeddingTimelineItem,
} from '@/profiles/casamento/casamento-types'
import type { WeddingProposalStatusId } from '@/profiles/casamento/wedding-proposal-status'

type ProposalPage = { items: WeddingProposal[]; total: number; page: number; pageSize: number }

export type OpenProposalInput = {
  contactId?: string | null
  customerName?: string | null
  plannerId?: string | null
  weddingStyle?: string | null
  weddingDate?: string | null // yyyy-MM-dd
  guestCount?: number | null
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

export type AddChecklistInput = {
  title: string
  description?: string | null
  dueDate?: string | null // yyyy-MM-dd
}

export type UpdateChecklistInput = {
  title?: string
  description?: string | null
  clearDescription?: boolean
  dueDate?: string | null
  clearDueDate?: boolean
}

export function listProposals(
  opts: {
    status?: string
    plannerId?: string
    contactId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<ProposalPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.plannerId) p.set('plannerId', opts.plannerId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ProposalPage>(`/api/casamento/proposals${qs ? `?${qs}` : ''}`)
}

export function getProposal(id: string): Promise<WeddingProposal> {
  return apiFetch<WeddingProposal>(`/api/casamento/proposals/${id}`)
}

export function openProposal(input: OpenProposalInput): Promise<WeddingProposal> {
  return apiFetch<WeddingProposal>('/api/casamento/proposals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

// ---- Itens de ORÇAMENTO (entram no total) ----

export function addItem(proposalId: string, input: AddItemInput): Promise<WeddingProposalItem> {
  return apiFetch<WeddingProposalItem>(`/api/casamento/proposals/${proposalId}/items`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateItem(
  proposalId: string,
  itemId: string,
  input: UpdateItemInput,
): Promise<WeddingProposalItem> {
  return apiFetch<WeddingProposalItem>(`/api/casamento/proposals/${proposalId}/items/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteItem(proposalId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/proposals/${proposalId}/items/${itemId}`, {
    method: 'DELETE',
  })
}

// ---- Marcos de CRONOGRAMA (NÃO entram no total) ----

export function addTimelineItem(
  proposalId: string,
  input: AddTimelineInput,
): Promise<WeddingTimelineItem> {
  return apiFetch<WeddingTimelineItem>(`/api/casamento/proposals/${proposalId}/timeline`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateTimelineItem(
  proposalId: string,
  itemId: string,
  input: UpdateTimelineInput,
): Promise<WeddingTimelineItem> {
  return apiFetch<WeddingTimelineItem>(
    `/api/casamento/proposals/${proposalId}/timeline/${itemId}`,
    {
      method: 'PATCH',
      body: JSON.stringify(input),
    },
  )
}

export function deleteTimelineItem(proposalId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/proposals/${proposalId}/timeline/${itemId}`, {
    method: 'DELETE',
  })
}

// ---- Tarefas do CHECKLIST pré-casamento (a escapada — NÃO entram no total, ordenadas por due_date) ----

export function addChecklistTask(
  proposalId: string,
  input: AddChecklistInput,
): Promise<WeddingChecklistTask> {
  return apiFetch<WeddingChecklistTask>(`/api/casamento/proposals/${proposalId}/checklist`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateChecklistTask(
  proposalId: string,
  taskId: string,
  input: UpdateChecklistInput,
): Promise<WeddingChecklistTask> {
  return apiFetch<WeddingChecklistTask>(
    `/api/casamento/proposals/${proposalId}/checklist/${taskId}`,
    {
      method: 'PATCH',
      body: JSON.stringify(input),
    },
  )
}

export function deleteChecklistTask(proposalId: string, taskId: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/proposals/${proposalId}/checklist/${taskId}`, {
    method: 'DELETE',
  })
}

export function toggleChecklistTask(
  proposalId: string,
  taskId: string,
  done: boolean,
): Promise<WeddingChecklistTask> {
  return apiFetch<WeddingChecklistTask>(
    `/api/casamento/proposals/${proposalId}/checklist/${taskId}/toggle`,
    {
      method: 'PATCH',
      body: JSON.stringify({ done }),
    },
  )
}

// ---- Cupom na proposta (onda 1, backlog #10 — aplicado pelo painel) ----

export function applyCoupon(id: string, code: string): Promise<WeddingProposal> {
  return apiFetch<WeddingProposal>(`/api/casamento/proposals/${id}/coupon`, {
    method: 'PATCH',
    body: JSON.stringify({ code }),
  })
}

export function removeCoupon(id: string): Promise<WeddingProposal> {
  return apiFetch<WeddingProposal>(`/api/casamento/proposals/${id}/coupon`, { method: 'DELETE' })
}

// ---- Status ----

export function updateProposalStatus(
  id: string,
  newStatus: WeddingProposalStatusId,
): Promise<WeddingProposal> {
  return apiFetch<WeddingProposal>(`/api/casamento/proposals/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
