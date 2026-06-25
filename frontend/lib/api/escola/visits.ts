import { apiFetch } from '@/lib/api/client'
import type { EscolaVisitStatusId } from '@/profiles/escola/escola-visit-status'
import type { EscolaVisit, EscolaPeriod } from '@/profiles/escola/escola-types'

type VisitPage = { items: EscolaVisit[]; total: number; page: number; pageSize: number }

export type CreateVisitInput = {
  visitorName: string
  visitorPhone?: string | null
  visitDate: string // "YYYY-MM-DD"
  period: EscolaPeriod
  numPeople?: number | null
  studentId?: string | null
  notes?: string | null
}

export function listVisits(
  opts: { status?: string; date?: string; page?: number; pageSize?: number } = {},
): Promise<VisitPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.date) p.set('date', opts.date)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<VisitPage>(`/api/escola/visits${qs ? `?${qs}` : ''}`)
}

export function getVisit(id: string): Promise<EscolaVisit> {
  return apiFetch<EscolaVisit>(`/api/escola/visits/${id}`)
}

export function createVisit(input: CreateVisitInput): Promise<EscolaVisit> {
  return apiFetch<EscolaVisit>('/api/escola/visits', { method: 'POST', body: JSON.stringify(input) })
}

export function updateVisitStatus(id: string, newStatus: EscolaVisitStatusId): Promise<EscolaVisit> {
  return apiFetch<EscolaVisit>(`/api/escola/visits/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}
