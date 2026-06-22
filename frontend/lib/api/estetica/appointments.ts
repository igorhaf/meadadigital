import { apiFetch } from '@/lib/api/client'
import type { AestheticAppointmentStatusId } from '@/profiles/estetica/aesthetic-appointment-status'
import type { AestheticAppointment, AestheticSessionNote } from '@/profiles/estetica/estetica-types'

type AppointmentPage = { items: AestheticAppointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  professionalId: string
  procedureId: string
  packageId?: string | null
  guestName: string
  guestPhone?: string | null
  startAt: string // ISO instant
  notes?: string | null
}

export type SessionNoteInput = {
  treatedArea?: string | null
  deviceParams?: string | null
  observations?: string | null
}

export function listAppointments(
  opts: {
    status?: string; dateFrom?: string; dateTo?: string
    professionalId?: string; contactId?: string; page?: number; pageSize?: number
  } = {},
): Promise<AppointmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.professionalId) p.set('professionalId', opts.professionalId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<AppointmentPage>(`/api/estetica/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<AestheticAppointment> {
  return apiFetch<AestheticAppointment>(`/api/estetica/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<AestheticAppointment> {
  return apiFetch<AestheticAppointment>('/api/estetica/appointments', { method: 'POST', body: JSON.stringify(input) })
}

export function updateAppointmentStatus(id: string, newStatus: AestheticAppointmentStatusId): Promise<AestheticAppointment> {
  return apiFetch<AestheticAppointment>(`/api/estetica/appointments/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}

// ---- Ficha/evolução por sessão (1:1 com o agendamento) ----

export function getSessionNote(appointmentId: string): Promise<AestheticSessionNote> {
  return apiFetch<AestheticSessionNote>(`/api/estetica/appointments/${appointmentId}/note`)
}

export function upsertSessionNote(appointmentId: string, input: SessionNoteInput): Promise<AestheticSessionNote> {
  return apiFetch<AestheticSessionNote>(`/api/estetica/appointments/${appointmentId}/note`, {
    method: 'PUT', body: JSON.stringify(input),
  })
}
