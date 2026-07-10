import type { SalonAppointmentStatusId } from './salon-appointment-status'

/** Profissional (espelha SalonProfessional). */
export type Professional = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Serviço oferecido (espelha SalonOffering). priceCents nullable. */
export type Offering = {
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

/** Config do salão (espelha SalonConfig). opensAt/closesAt em "HH:MM:SS". */
export type Config = {
  companyId: string
  opensAt: string
  closesAt: string
  bufferMinutes: number
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
}

/** Agendamento (espelha SalonAppointment). startAt/endAt em ISO-8601 instant. */
export type Appointment = {
  id: string
  professionalId: string
  professionalName: string
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
  status: SalonAppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_slot. */
export type ConflictDetail = {
  appointmentId: string
  guestName: string
  startAt: string
  endAt: string
}

/** Formata centavos em R$ pt-BR (—  se null). */
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
