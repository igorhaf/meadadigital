import type { AppointmentStatusId } from './appointment-status'

/** Paciente (espelha DentalPatient do backend). notes é ADMINISTRATIVO, não clínico (LGPD). */
export type Patient = {
  id: string
  name: string
  email: string | null
  phone: string | null
  document: string | null
  birthDate: string | null // "YYYY-MM-DD"
  contactId: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do consultório (espelha DentalClinicConfig). opensAt/closesAt em "HH:MM:SS". */
export type ClinicConfig = {
  companyId: string
  durationMinutes: number
  bufferMinutes: number
  opensAt: string
  closesAt: string
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  recallEnabled: boolean
  recallMonths: number
}

/** Consulta (espelha DentalAppointment). startAt/endAt em ISO-8601 instant. */
export type Appointment = {
  id: string
  patientId: string
  patientName: string
  conversationId: string | null
  startAt: string
  endAt: string
  durationMinutes: number
  type: string
  status: AppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_slot. */
export type ConflictDetail = {
  appointmentId: string
  patientName: string
  startAt: string
  endAt: string
}

/** Sugestões de tipo de consulta (datalist do form — texto livre, não impõe). */
export const TYPE_SUGGESTIONS = [
  'Limpeza',
  'Avaliação',
  'Restauração',
  'Canal',
  'Ortodontia',
  'Manutenção',
  'Retorno',
] as const

/** Calcula a idade a partir da data de nascimento "YYYY-MM-DD" (null se ausente/ inválida). */
export function calcularIdade(birthDate: string | null): number | null {
  if (!birthDate) return null
  const b = new Date(birthDate)
  if (Number.isNaN(b.getTime())) return null
  const now = new Date()
  let age = now.getFullYear() - b.getFullYear()
  const m = now.getMonth() - b.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < b.getDate())) age--
  return age
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
