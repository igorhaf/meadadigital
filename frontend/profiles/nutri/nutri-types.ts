import type { NutriAppointmentStatusId } from './nutri-appointment-status'

/** Nutricionista (espelha NutriProfessional). */
export type NutriProfessional = {
  id: string
  name: string
  specialty: string | null
  crn: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do consultório (espelha NutriConfig). opensAt/closesAt em "HH:MM:SS". */
export type NutriConfig = {
  companyId: string
  opensAt: string
  closesAt: string
  bufferMinutes: number
}

/** Paciente — sub-entidade do contato (nível 1). Espelha NutriPatient. */
export type NutriPatient = {
  id: string
  contactId: string
  name: string
  goal: string | null
  dietaryRestrictions: string | null
  birthDate: string | null
  notes: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Status do plano. */
export type NutriPlanStatus = 'ativo' | 'arquivado'

/** Plano alimentar — sub-entidade do paciente (nível 2). Espelha NutriPlan. body é o texto do
 * profissional; a IA entrega exato, nunca edita. */
export type NutriPlan = {
  id: string
  patientId: string
  professionalId: string | null
  professionalName: string | null
  title: string
  body: string
  startsOn: string | null
  endsOn: string | null
  status: NutriPlanStatus
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Tipo de consulta. */
export type NutriAppointmentType = 'primeira' | 'retorno' | 'avaliacao'

/** Consulta (espelha NutriAppointment). startAt/endAt em ISO-8601 instant. */
export type NutriAppointment = {
  id: string
  professionalId: string
  professionalName: string
  patientId: string
  patientName: string
  patientPhone: string | null
  contactId: string | null
  conversationId: string | null
  appointmentType: NutriAppointmentType
  durationMinutes: number
  startAt: string
  endAt: string
  status: NutriAppointmentStatusId
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

const TYPE_LABELS: Record<NutriAppointmentType, string> = {
  primeira: 'Primeira consulta',
  retorno: 'Retorno',
  avaliacao: 'Avaliação',
}
export function typeLabel(t: string | null | undefined): string {
  if (!t) return '—'
  return TYPE_LABELS[t as NutriAppointmentType] ?? t
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
