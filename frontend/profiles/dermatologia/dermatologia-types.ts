import type { DermatologiaAppointmentStatusId } from './dermatologia-appointment-status'

/** Dermatologista (espelha DermatologiaProfessional). */
export type DermatologiaProfessional = {
  id: string
  name: string
  specialty: string | null
  crmRqe: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do consultório (espelha DermatologiaConfig). opensAt/closesAt em "HH:MM:SS". SEM duration. */
export type DermatologiaConfig = {
  companyId: string
  opensAt: string
  closesAt: string
  bufferMinutes: number
}

/**
 * Tipo de atendimento — a ESCAPADA (espelha DermatologiaProcedureType). Duração POR TIPO.
 * prepInstructions é a orientação PRÉ-procedimento entregue VERBATIM ao paciente pela IA — NÃO é
 * prontuário (LGPD administrativo).
 */
export type DermatologiaProcedureType = {
  id: string
  name: string
  durationMinutes: number
  prepInstructions: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Paciente — sub-entidade do contato. Espelha DermatologiaPatient. */
export type DermatologiaPatient = {
  id: string
  contactId: string
  name: string
  birthDate: string | null
  notes: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Consulta (espelha DermatologiaAppointment). startAt/endAt em ISO-8601 instant. */
export type DermatologiaAppointment = {
  id: string
  professionalId: string
  professionalName: string
  patientId: string
  patientName: string
  patientPhone: string | null
  procedureTypeId: string
  procedureTypeName: string
  contactId: string | null
  conversationId: string | null
  durationMinutes: number
  startAt: string
  endAt: string
  status: DermatologiaAppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_slot (por profissional). */
export type ConflictDetail = {
  appointmentId: string
  patientName: string
  startAt: string
  endAt: string
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
