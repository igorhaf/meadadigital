import { apiFetch } from '@/lib/api/client'
import type { SalonAppointmentStatusId } from '@/profiles/salon/salon-appointment-status'
import type { Appointment } from '@/profiles/salon/salon-types'

type AppointmentPage = { items: Appointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  professionalId: string
  serviceId: string
  guestName: string
  guestPhone?: string | null
  startAt: string // ISO-8601 instant
  notes?: string | null
}

export function listAppointments(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    professionalId?: string
    contactId?: string
    page?: number
    pageSize?: number
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
  return apiFetch<AppointmentPage>(`/api/salon/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/salon/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<Appointment> {
  return apiFetch<Appointment>('/api/salon/appointments', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateAppointmentStatus(
  id: string,
  newStatus: SalonAppointmentStatusId,
): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/salon/appointments/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
