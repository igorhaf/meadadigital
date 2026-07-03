import { apiFetch } from '@/lib/api/client'
import type { PousadaReservationStatusId } from '@/profiles/pousada/pousada-reservation-status'
import type { Reservation } from '@/profiles/pousada/pousada-types'

type ReservationPage = { items: Reservation[]; total: number; page: number; pageSize: number }

export type CreateReservationInput = {
  roomId: string
  guestName: string
  guestPhone?: string | null
  guestsCount: number
  checkIn: string // "YYYY-MM-DD"
  checkOut: string // "YYYY-MM-DD"
  notes?: string | null
}

export function listReservations(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    roomId?: string
    contactId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<ReservationPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.roomId) p.set('roomId', opts.roomId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ReservationPage>(`/api/pousada/reservations${qs ? `?${qs}` : ''}`)
}

export function getReservation(id: string): Promise<Reservation> {
  return apiFetch<Reservation>(`/api/pousada/reservations/${id}`)
}

export function createReservation(input: CreateReservationInput): Promise<Reservation> {
  return apiFetch<Reservation>('/api/pousada/reservations', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateReservationStatus(
  id: string,
  newStatus: PousadaReservationStatusId,
): Promise<Reservation> {
  return apiFetch<Reservation>(`/api/pousada/reservations/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
