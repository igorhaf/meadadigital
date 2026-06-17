import { apiFetch } from '@/lib/api/client'
import type { LegalCase, LegalCaseUpdate } from '@/profiles/legal/legal-types'
import type { LegalCaseStatusId } from '@/profiles/legal/legal-case-status'

type CasePage = { items: LegalCase[]; total: number; page: number; pageSize: number }

export type CreateCaseInput = {
  legalClientId: string
  cnjNumber: string
  title: string
  description?: string | null
  court?: string | null
  forum?: string | null
  subject?: string | null
}

export type UpdateCaseInput = Partial<Omit<CreateCaseInput, 'legalClientId' | 'cnjNumber'>>

export function listCases(
  opts: { status?: string; search?: string; page?: number; pageSize?: number } = {},
): Promise<CasePage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.search) p.set('search', opts.search)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<CasePage>(`/api/legal/cases${qs ? `?${qs}` : ''}`)
}

export function getCase(id: string): Promise<LegalCase> {
  return apiFetch<LegalCase>(`/api/legal/cases/${id}`)
}

export function createCase(input: CreateCaseInput): Promise<LegalCase> {
  return apiFetch<LegalCase>('/api/legal/cases', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCase(id: string, input: UpdateCaseInput): Promise<LegalCase> {
  return apiFetch<LegalCase>(`/api/legal/cases/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function updateCaseStatus(id: string, newStatus: LegalCaseStatusId): Promise<LegalCase> {
  return apiFetch<LegalCase>(`/api/legal/cases/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}

export function addUpdate(
  caseId: string,
  input: { title: string; body?: string | null; occurredAt?: string | null },
): Promise<LegalCaseUpdate> {
  return apiFetch<LegalCaseUpdate>(`/api/legal/cases/${caseId}/updates`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function deleteUpdate(caseId: string, updateId: string): Promise<void> {
  return apiFetch<void>(`/api/legal/cases/${caseId}/updates/${updateId}`, { method: 'DELETE' })
}
