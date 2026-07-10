import { apiFetch } from '@/lib/api/client'
import type { AppointmentStatusId } from '@/profiles/dental/appointment-status'
import type { Appointment } from '@/profiles/dental/dental-types'

type AppointmentPage = { items: Appointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  patientId: string
  startAt: string // ISO-8601 instant
  type: string
  notes?: string | null
}

export function listAppointments(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    patientId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<AppointmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.patientId) p.set('patientId', opts.patientId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<AppointmentPage>(`/api/dental/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/dental/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<Appointment> {
  return apiFetch<Appointment>('/api/dental/appointments', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateAppointmentStatus(
  id: string,
  newStatus: AppointmentStatusId,
): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/dental/appointments/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
