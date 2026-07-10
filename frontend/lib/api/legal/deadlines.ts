import { apiFetch } from '@/lib/api/client'

export type LegalDeadline = {
  id: string
  caseId: string
  caseTitle: string
  kind: 'prazo' | 'audiencia'
  title: string
  dueDate: string
  dueTime: string | null
  location: string | null
  status: 'pendente' | 'cumprido' | 'perdido'
  notes: string | null
  createdAt: string
  updatedAt: string
}

export type CreateDeadlineInput = {
  caseId: string
  kind: string
  title: string
  dueDate: string
  dueTime?: string | null
  location?: string | null
  notes?: string | null
}

export type UpdateDeadlineInput = Partial<Omit<CreateDeadlineInput, 'caseId'>> & {
  status?: string
  clearDueTime?: boolean
}

export function listDeadlines(
  opts: { status?: string; caseId?: string } = {},
): Promise<{ items: LegalDeadline[] }> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.caseId) p.set('caseId', opts.caseId)
  const qs = p.toString()
  return apiFetch<{ items: LegalDeadline[] }>(`/api/legal/deadlines${qs ? `?${qs}` : ''}`)
}

export function createDeadline(input: CreateDeadlineInput): Promise<LegalDeadline> {
  return apiFetch<LegalDeadline>('/api/legal/deadlines', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateDeadline(id: string, input: UpdateDeadlineInput): Promise<LegalDeadline> {
  return apiFetch<LegalDeadline>(`/api/legal/deadlines/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteDeadline(id: string): Promise<void> {
  return apiFetch<void>(`/api/legal/deadlines/${id}`, { method: 'DELETE' })
}
