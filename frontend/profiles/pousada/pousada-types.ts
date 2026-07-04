import type { PousadaReservationStatusId } from './pousada-reservation-status'

/** Quarto (espelha PousadaRoom). */
export type Room = {
  id: string
  name: string
  capacity: number
  nightlyRateCents: number
  description: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config (espelha PousadaConfig). checkInTime/checkOutTime em "HH:MM:SS". */
export type Config = {
  companyId: string
  checkInTime: string
  checkOutTime: string
  cancellationPolicy: string | null
  reminderEnabled: boolean
  autoTransitionEnabled: boolean
}

/** Reserva (espelha PousadaReservation). checkInDate/checkOutDate em "YYYY-MM-DD". */
export type Reservation = {
  id: string
  roomId: string
  roomName: string
  conversationId: string | null
  contactId: string | null
  guestName: string
  guestPhone: string | null
  guestsCount: number
  checkInDate: string
  checkOutDate: string
  nights: number
  nightlyRateCents: number
  capacitySnapshot: number
  totalCents: number
  status: PousadaReservationStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_dates. */
export type ConflictDetail = {
  reservationId: string
  guestName: string
  checkInDate: string
  checkOutDate: string
  roomName: string
}

/** Formata centavos em R$ pt-BR (— se null). */
export function formatPrice(cents: number | null): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** Formata "YYYY-MM-DD" em "DD/MM/AAAA" (sem fuso — é dia). */
export function formatDate(d: string): string {
  if (!d) return '—'
  const [y, m, day] = d.split('-')
  return `${day}/${m}/${y}`
}

/** Noites entre duas datas "YYYY-MM-DD" (0 se inválido/ não-positivo). */
export function computeNights(checkIn: string, checkOut: string): number {
  if (!checkIn || !checkOut) return 0
  const a = new Date(checkIn + 'T00:00:00')
  const b = new Date(checkOut + 'T00:00:00')
  const diff = Math.round((b.getTime() - a.getTime()) / 86_400_000)
  return diff > 0 ? diff : 0
}
