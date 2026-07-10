import type { AestheticAppointmentStatusId } from './aesthetic-appointment-status'
import type { AestheticPackageStatusId } from './aesthetic-package-status'

/** Profissional (espelha AestheticProfessional). */
export type AestheticProfessional = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Procedimento (espelha AestheticProcedure). unitPriceCents = preço de UMA sessão. */
export type AestheticProcedure = {
  id: string
  name: string
  category: string | null
  durationMinutes: number
  unitPriceCents: number
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config (espelha AestheticConfig). opensAt/closesAt em "HH:MM:SS". */
export type AestheticConfig = {
  companyId: string
  opensAt: string
  closesAt: string
  slotMinutes: number
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  autoExpireEnabled: boolean
  packageValidityDays: number | null
  renewalEnabled: boolean
  renewalDays: number
  expiryWarningDays: number
}

/** Pacote multi-sessão (espelha AestheticPackage) — a escapada. saldo materializado. */
export type AestheticPackage = {
  id: string
  contactId: string | null
  procedureId: string
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  procedureName: string
  unitPriceCents: number
  totalSessions: number
  sessionsUsed: number
  sessionsRemaining: number
  totalCents: number
  status: AestheticPackageStatusId
  notes: string | null
  validUntil: string | null
  purchasedAt: string
  activatedAt: string | null
  statusUpdatedAt: string
}

/** Agendamento (espelha AestheticAppointment). packageId null = avulso; consumedSession abateu saldo. */
export type AestheticAppointment = {
  id: string
  professionalId: string
  professionalName: string
  procedureId: string
  procedureName: string
  packageId: string | null
  consumedSession: boolean
  conversationId: string | null
  contactId: string | null
  guestName: string
  guestPhone: string | null
  startAt: string
  endAt: string
  durationMinutes: number
  status: AestheticAppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Ficha/evolução por sessão (espelha AestheticSessionNote). 1:1 com o agendamento. Sem foto. */
export type AestheticSessionNote = {
  id: string
  appointmentId: string
  treatedArea: string | null
  deviceParams: string | null
  observations: string | null
  createdAt: string
  updatedAt: string
}

/** Formata centavos em R$ pt-BR. */
export function formatPrice(cents: number | null | undefined): string {
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

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}
