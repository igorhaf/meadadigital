import type { AcademiaMembershipStatusId } from './academia-membership-status'

/** Plano mensal (espelha AcademiaPlan). */
export type Plan = {
  id: string
  name: string
  monthlyCents: number
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Aula semanal (espelha AcademiaClass + remainingSlots computado pelo controller). */
export type Class = {
  id: string
  name: string
  modality: string
  dayOfWeek: number // 0=domingo..6=sábado
  startTime: string // "HH:MM:SS"
  durationMinutes: number
  capacity: number
  instructor: string | null
  active: boolean
  remainingSlots: number
  createdAt: string
  updatedAt: string
}

/** Config (espelha AcademiaConfig). opensAt/closesAt em "HH:MM:SS". */
export type Config = {
  companyId: string
  opensAt: string
  closesAt: string
}

/** Entrada da junction matrícula↔aula (snapshot). */
export type MembershipClassEntry = {
  classId: string
  className: string
  dayOfWeek: number
  startTime: string
  durationMinutes: number
  modality: string
}

/** Matrícula (espelha AcademiaMembership). */
export type Membership = {
  id: string
  planId: string
  planName: string
  planMonthlyCents: number
  conversationId: string | null
  contactId: string | null
  studentName: string
  studentPhone: string | null
  startDate: string
  endDate: string | null
  status: AcademiaMembershipStatusId
  notes: string | null
  classes: MembershipClassEntry[]
  createdAt: string
  statusUpdatedAt: string
}

/** Pagamento (espelha AcademiaPayment). referenceMonth em "YYYY-MM-DD". */
export type Payment = {
  id: string
  membershipId: string
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

/** Detalhe do conflito no 409 class_full. */
export type ConflictDetail = {
  classId: string
  className: string
}

const DAYS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado']

/** Rótulo do dia da semana (0=domingo). */
export function dayOfWeekLabel(dow: number): string {
  return dow >= 0 && dow <= 6 ? DAYS[dow] : String(dow)
}

/** Formata centavos em R$ pt-BR. */
export function formatPrice(cents: number | null): string {
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
