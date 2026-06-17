import { apiFetch } from '@/lib/api/client'
import type { PetAppointmentStatusId } from '@/profiles/pet/pet-appointment-status'
import type { PetAppointment } from '@/profiles/pet/pet-types'

type AppointmentPage = { items: PetAppointment[]; total: number; page: number; pageSize: number }

export type CreateAppointmentInput = {
  professionalId: string
  serviceId: string
  animalId: string
  startAt: string // ISO-8601 instant
  notes?: string | null
}

export function listAppointments(
  opts: {
    status?: string; dateFrom?: string; dateTo?: string; professionalId?: string
    animalId?: string; contactId?: string; page?: number; pageSize?: number
  } = {},
): Promise<AppointmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.professionalId) p.set('professionalId', opts.professionalId)
  if (opts.animalId) p.set('animalId', opts.animalId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<AppointmentPage>(`/api/pet/appointments${qs ? `?${qs}` : ''}`)
}

export function getAppointment(id: string): Promise<PetAppointment> {
  return apiFetch<PetAppointment>(`/api/pet/appointments/${id}`)
}

export function createAppointment(input: CreateAppointmentInput): Promise<PetAppointment> {
  return apiFetch<PetAppointment>('/api/pet/appointments', { method: 'POST', body: JSON.stringify(input) })
}

export function updateAppointmentStatus(id: string, newStatus: PetAppointmentStatusId): Promise<PetAppointment> {
  return apiFetch<PetAppointment>(`/api/pet/appointments/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}
