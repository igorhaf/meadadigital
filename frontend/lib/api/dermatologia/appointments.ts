import { apiFetch } from '@/lib/api/client'
import type { DermatologiaAppointmentStatusId } from '@/profiles/dermatologia/dermatologia-appointment-status'
import type { DermatologiaAppointment } from '@/profiles/dermatologia/dermatologia-types'

type AppointmentPage = { items: DermatologiaAppointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  professionalId: string
  patientId: string
  procedureTypeId: string
  startAt: string // ISO-8601 instant
  notes?: string | null
}

export function listAppointments(
  opts: {
    status?: string; dateFrom?: string; dateTo?: string; professionalId?: string
    patientId?: string; contactId?: string; page?: number; pageSize?: number
  } = {},
): Promise<AppointmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.professionalId) p.set('professionalId', opts.professionalId)
  if (opts.patientId) p.set('patientId', opts.patientId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<AppointmentPage>(`/api/dermatologia/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<DermatologiaAppointment> {
  return apiFetch<DermatologiaAppointment>(`/api/dermatologia/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<DermatologiaAppointment> {
  return apiFetch<DermatologiaAppointment>('/api/dermatologia/appointments', { method: 'POST', body: JSON.stringify(input) })
}

export function updateAppointmentStatus(id: string, newStatus: DermatologiaAppointmentStatusId): Promise<DermatologiaAppointment> {
  return apiFetch<DermatologiaAppointment>(`/api/dermatologia/appointments/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}
