import { apiFetch } from '@/lib/api/client'
import type { ReservationStatusId } from '@/profiles/restaurant/reservation-status'
import type { Reservation } from '@/profiles/restaurant/restaurant-types'

type ReservationPage = { items: Reservation[]; total: number; page: number; pageSize: number }

export type CreateReservationInput = {
  tableId: string
  guestName: string
  guestPhone?: string | null
  startAt: string // ISO-8601 instant
  numPeople: number
  notes?: string | null
}

export function listReservations(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<ReservationPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ReservationPage>(`/api/restaurant/reservations${qs ? `?${qs}` : ''}`)
}

export function getReservation(id: string): Promise<Reservation> {
  return apiFetch<Reservation>(`/api/restaurant/reservations/${id}`)
}

export function createReservation(input: CreateReservationInput): Promise<Reservation> {
  return apiFetch<Reservation>('/api/restaurant/reservations', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateReservationStatus(
  id: string,
  newStatus: ReservationStatusId,
): Promise<Reservation> {
  return apiFetch<Reservation>(`/api/restaurant/reservations/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
