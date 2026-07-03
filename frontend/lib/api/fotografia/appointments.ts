import { apiFetch } from '@/lib/api/client'
import type { FotografiaAppointmentStatusId } from '@/profiles/fotografia/fotografia-appointment-status'
import type { FotografiaSession } from '@/profiles/fotografia/fotografia-types'

type SessionPage = { items: FotografiaSession[]; total: number; page: number; pageSize: number }

export type CreateSessionInput = {
  professionalId: string
  packageId: string
  customerName: string
  customerPhone?: string | null
  startAt: string // ISO-8601 instant
  notes?: string | null
}

/** Atualização do material/observações de uma sessão (o write do delivery_link). */
export type UpdateSessionInput = {
  deliveryLink?: string | null
  notes?: string | null
}

export function listSessions(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    professionalId?: string
    contactId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<SessionPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.professionalId) p.set('professionalId', opts.professionalId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<SessionPage>(`/api/fotografia/sessions${qs ? `?${qs}` : ''}`)
}

export function getSession(id: string): Promise<FotografiaSession> {
  return apiFetch<FotografiaSession>(`/api/fotografia/sessions/${id}`)
}

export function createSession(input: CreateSessionInput): Promise<FotografiaSession> {
  return apiFetch<FotografiaSession>('/api/fotografia/sessions', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateSessionStatus(
  id: string,
  newStatus: FotografiaAppointmentStatusId,
): Promise<FotografiaSession> {
  return apiFetch<FotografiaSession>(`/api/fotografia/sessions/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}

/** Grava/edita o link do material entregue e/ou as observações da sessão. */
export function updateSession(id: string, input: UpdateSessionInput): Promise<FotografiaSession> {
  return apiFetch<FotografiaSession>(`/api/fotografia/sessions/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
