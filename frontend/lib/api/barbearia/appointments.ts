import { apiFetch } from '@/lib/api/client'
import type { BarberAppointmentStatusId } from '@/profiles/barbearia/barber-appointment-status'
import type { Appointment } from '@/profiles/barbearia/barber-types'

type AppointmentPage = { items: Appointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  barberId: string
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
    barberId?: string
    contactId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<AppointmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.barberId) p.set('barberId', opts.barberId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<AppointmentPage>(`/api/barbearia/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/barbearia/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<Appointment> {
  return apiFetch<Appointment>('/api/barbearia/appointments', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateAppointmentStatus(
  id: string,
  newStatus: BarberAppointmentStatusId,
): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/barbearia/appointments/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
