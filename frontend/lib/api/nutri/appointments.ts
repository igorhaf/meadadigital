import { apiFetch } from '@/lib/api/client'
import type { NutriAppointmentStatusId } from '@/profiles/nutri/nutri-appointment-status'
import type { NutriAppointment, NutriAppointmentType } from '@/profiles/nutri/nutri-types'

type AppointmentPage = { items: NutriAppointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  professionalId: string
  patientId: string
  appointmentType: NutriAppointmentType
  startAt: string // ISO-8601 instant
  durationMinutes?: number | null
  notes?: string | null
}

export function listAppointments(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    professionalId?: string
    patientId?: string
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
  if (opts.patientId) p.set('patientId', opts.patientId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<AppointmentPage>(`/api/nutri/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<NutriAppointment> {
  return apiFetch<NutriAppointment>(`/api/nutri/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<NutriAppointment> {
  return apiFetch<NutriAppointment>('/api/nutri/appointments', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateAppointmentStatus(
  id: string,
  newStatus: NutriAppointmentStatusId,
): Promise<NutriAppointment> {
  return apiFetch<NutriAppointment>(`/api/nutri/appointments/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
