import type { BarberAppointmentStatusId } from './barber-appointment-status'
import type { BarberQueueStatusId } from './barber-queue-status'

/** Barbeiro (espelha BarberBarber). */
export type Barber = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Serviço oferecido (espelha BarberService). priceCents nullable. */
export type Service = {
  id: string
  name: string
  category: string | null
  durationMinutes: number
  priceCents: number | null
  active: boolean
  description: string | null
  createdAt: string
  updatedAt: string
}

/** Config da barbearia (espelha BarberConfig). opensAt/closesAt em "HH:MM:SS". */
export type Config = {
  companyId: string
  opensAt: string
  closesAt: string
  slotMinutes: number
  queueEnabled: boolean
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  upsellEnabled: boolean
  reactivationEnabled: boolean
  reactivationDays: number
  reactivationCouponCode: string | null
  postReviewEnabled: boolean
  reviewLink: string | null
  reviewCooldownDays: number
}

/** Agendamento (espelha BarberAppointment). startAt/endAt em ISO-8601 instant. */
export type Appointment = {
  id: string
  barberId: string
  barberName: string
  serviceId: string
  serviceName: string
  conversationId: string | null
  contactId: string | null
  guestName: string
  guestPhone: string | null
  startAt: string
  endAt: string
  durationMinutes: number
  priceCents: number | null
  discountCents: number
  couponCodeSnapshot: string | null
  loyaltyApplied: boolean
  status: BarberAppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Cupom de desconto (onda 1, backlog #12 — espelha BarberCoupon; clone do motor adega/atelie). */
export type BarberCoupon = {
  id: string
  companyId: string
  code: string
  kind: 'percent' | 'fixed'
  value: number
  minOrderCents: number
  maxUses: number | null
  uses: number
  validUntil: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Fidelidade "a cada N cortes, 1 grátis" (onda 1, backlog #3 — espelha BarberLoyaltyConfig). */
export type BarberLoyaltyConfig = {
  companyId: string
  enabled: boolean
  thresholdCuts: number
}

/** Linha agregada do relatório (onda 1, backlog #15). */
export type BarberReportRow = {
  month?: string
  barberName?: string
  serviceName?: string
  count: number
  noShows?: number
  totalCents: number
}

export type BarberReportSummary = {
  months: number
  realizedCount: number
  noShowCount: number
  cancelledCount: number
  totalCents: number
  byMonth: BarberReportRow[]
  byBarber: BarberReportRow[]
  byService: BarberReportRow[]
}

/**
 * Ticket da fila de walk-in (espelha BarberQueueTicket). barberId null = "qualquer barbeiro".
 * position/etaMinutes são DERIVADOS pelo backend (não persistidos) — só vêm preenchidos para
 * tickets 'aguardando'.
 */
export type QueueTicket = {
  id: string
  barberId: string | null
  barberName: string | null
  serviceId: string
  serviceName: string
  durationMinutes: number
  conversationId: string | null
  contactId: string | null
  guestName: string
  guestPhone: string | null
  status: BarberQueueStatusId
  enqueuedAt: string
  calledAt: string | null
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
  position: number | null
  etaMinutes: number | null
}

/** Detalhe do conflito no 409 conflict_slot. */
export type ConflictDetail = {
  appointmentId: string
  guestName: string
  startAt: string
  endAt: string
}

/** Formata centavos em R$ pt-BR (— se null). */
export function formatPrice(cents: number | null): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}
