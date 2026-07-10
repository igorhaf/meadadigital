import type { EscolaEnrollmentStatusId } from './escola-enrollment-status'
import type { EscolaVisitStatusId } from './escola-visit-status'

/** Turno de uma turma (espelha o CHECK de escola_classes.shift). */
export type EscolaShift = 'manha' | 'tarde' | 'integral'
/** Período de uma visita (espelha o CHECK de escola_visits.period). */
export type EscolaPeriod = 'manha' | 'tarde'

/** Turno disponível na turma + rótulo pt-BR. */
export const SHIFTS: { id: EscolaShift; label: string }[] = [
  { id: 'manha', label: 'Manhã' },
  { id: 'tarde', label: 'Tarde' },
  { id: 'integral', label: 'Integral' },
]

/** Período disponível na visita + rótulo pt-BR. */
export const PERIODS: { id: EscolaPeriod; label: string }[] = [
  { id: 'manha', label: 'Manhã' },
  { id: 'tarde', label: 'Tarde' },
]

/** Turma (espelha EscolaClass + occupied computado pelo controller, se houver). */
export type EscolaClass = {
  id: string
  companyId: string
  name: string
  grade: string
  shift: EscolaShift
  capacity: number
  monthlyCents: number
  year: number | null
  description: string | null
  active: boolean
  // Vagas ocupadas (matrículas não-canceladas) — opcional; o controller pode anexar.
  occupied?: number
  remainingSlots?: number
  createdAt: string
  updatedAt: string
}

/** Aluno — sub-entidade do responsável (contato) (espelha EscolaStudent). */
export type EscolaStudent = {
  id: string
  companyId: string
  contactId: string
  name: string
  birthDate: string | null
  intendedGrade: string | null
  notes: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Matrícula (espelha EscolaEnrollment, com snapshots da turma + aluno + responsável). */
export type EscolaEnrollment = {
  id: string
  companyId: string
  classId: string
  studentId: string
  conversationId: string | null
  contactId: string | null
  studentName: string
  responsibleName: string | null
  className: string
  classGrade: string
  classShift: EscolaShift
  classMonthlyCents: number
  startDate: string
  endDate: string | null
  status: EscolaEnrollmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Pagamento mensal (espelha EscolaPayment). referenceMonth em "YYYY-MM-DD" (dia 01). */
export type EscolaPayment = {
  id: string
  enrollmentId: string
  referenceMonth: string
  paidAt: string
  amountCents: number
  method: string | null
  notes: string | null
}

/** Resumo de pagamentos de uma matrícula. */
export type EscolaPaymentSummary = {
  lastPaidMonth: string | null
  monthsOpen: number
  totalPayments: number
}

/** Visita (espelha EscolaVisit). visitDate em "YYYY-MM-DD". */
export type EscolaVisit = {
  id: string
  companyId: string
  conversationId: string | null
  contactId: string | null
  studentId: string | null
  visitorName: string
  visitorPhone: string | null
  visitDate: string
  period: EscolaPeriod
  numPeople: number | null
  status: EscolaVisitStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Config (espelha EscolaConfig). opensAt/closesAt em "HH:MM:SS". */
export type EscolaConfig = {
  businessName: string | null
  opensAt: string
  closesAt: string
  notes: string | null
  visitReminderEnabled: boolean
  visitAutoCompleteEnabled: boolean
  paymentReminderEnabled: boolean
  paymentDueDay: number
}

/** Entrada da lista de espera de uma turma (onda 1, backlog #1). Posição derivada. */
export type WaitlistEntry = {
  id: string
  studentName: string
  status: 'aguardando' | 'avisada'
  contactName: string
  contactPhone: string
  position: number
  createdAt: string
}

/** Detalhe do conflito no 409 class_full. */
export type ConflictDetail = {
  classId: string
  className: string
}

/** Rótulo pt-BR de um turno (fallback: o próprio id). */
export function shiftLabel(id: string | null | undefined): string {
  if (!id) return '—'
  return SHIFTS.find((s) => s.id === id)?.label ?? id
}

/** Rótulo pt-BR de um período (fallback: o próprio id). */
export function periodLabel(id: string | null | undefined): string {
  if (!id) return '—'
  return PERIODS.find((p) => p.id === id)?.label ?? id
}

/** Formata centavos em R$ pt-BR (— se null). */
export function formatBrl(cents: number | null): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** "YYYY-MM-DD" → "DD/MM/AAAA". */
export function formatDate(d: string | null): string {
  if (!d) return '—'
  const [y, m, day] = d.split('-')
  return `${day}/${m}/${y}`
}

/** "YYYY-MM-DD" → "MM/AAAA" (mês de referência). */
export function formatMonth(d: string | null): string {
  if (!d) return '—'
  const [y, m] = d.split('-')
  return `${m}/${y}`
}
