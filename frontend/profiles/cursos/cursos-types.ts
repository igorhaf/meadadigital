import type { CursoEnrollmentStatusId } from './curso-enrollment-status'

/** Curso (espelha CursoCourse). */
export type Course = {
  id: string
  title: string
  category: string
  monthlyCents: number
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Módulo ordenado de um curso (espelha CursoModule). position 0..N. */
export type Module = {
  id: string
  courseId: string
  position: number
  title: string
  content: string | null
  createdAt: string
  updatedAt: string
}

/** Config (espelha CursoConfig). opensAt/closesAt em "HH:MM:SS". */
export type Config = {
  companyId: string
  opensAt: string
  closesAt: string
  notes: string | null
  nudgeEnabled: boolean
  nudgeDays: number
  certificateBaseUrl: string | null
}

/** Progresso de uma matrícula: módulos concluídos sobre o total do curso. */
export type EnrollmentProgress = {
  done: number
  total: number
}

/** Matrícula em UM curso (espelha CursoEnrollment). */
export type Enrollment = {
  id: string
  courseId: string
  courseTitle: string
  courseCategory: string
  courseMonthlyCents: number
  conversationId: string | null
  contactId: string | null
  studentName: string
  studentPhone: string | null
  startDate: string
  endDate: string | null
  status: CursoEnrollmentStatusId
  notes: string | null
  progress: EnrollmentProgress
  nextModuleTitle: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Pagamento (espelha CursoPayment). referenceMonth em "YYYY-MM-DD". */
export type Payment = {
  id: string
  enrollmentId: string
  referenceMonth: string
  paidAt: string
  amountCents: number
  method: string | null
  notes: string | null
}

/** Resumo de pagamentos de uma matrícula. */
export type PaymentSummary = {
  lastPaidMonth: string | null
  monthsOpen: number
  totalPayments: number
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number | null): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** "HH:MM:SS" → "HH:MM". */
export function formatTime(t: string): string {
  return t?.slice(0, 5) ?? ''
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
