import type { ReservationStatusId } from './reservation-status'

/** Mesa (espelha RestaurantTable do backend). */
export type Table = {
  id: string
  label: string
  capacity: number
  available: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config de reservas (espelha RestaurantReservationConfig). opensAt/closesAt em "HH:MM:SS". */
export type ReservationConfig = {
  companyId: string
  durationMinutes: number
  bufferMinutes: number
  opensAt: string
  closesAt: string
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
}

/** Reserva (espelha Reservation). startAt/endAt em ISO-8601 instant. */
export type Reservation = {
  id: string
  tableId: string
  tableLabel: string
  conversationId: string | null
  contactId: string | null
  guestName: string
  guestPhone: string | null
  startAt: string
  endAt: string
  durationMinutes: number
  numPeople: number
  status: ReservationStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito devolvido no 409 conflict_slot (quem/quando ocupa o slot). */
export type ConflictDetail = {
  reservationId: string
  guestName: string
  startAt: string
  endAt: string
}

/** Formata um instante ISO em "DD/MM HH:MM" (pt-BR, fuso local do browser). */
export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Formata só a hora "HH:MM" de um instante ISO. */
export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

/** Formata só a data "DD/MM/AAAA" de um instante ISO. */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}
