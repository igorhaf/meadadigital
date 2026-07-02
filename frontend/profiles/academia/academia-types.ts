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

/** Check-in / presença (espelha AcademiaCheckin, migration 73). source = 'ia' | 'painel'. */
export type Checkin = {
  id: string
  membershipId: string
  classId: string
  checkinDate: string // "YYYY-MM-DD"
  checkinAt: string
  source: string
  notes: string | null
}

/** Status da lista de espera (migration 74). */
export type WaitlistStatusId = 'aguardando' | 'chamado' | 'matriculado' | 'desistiu'

/** Entrada na fila de espera de uma aula (espelha AcademiaWaitlistEntry). position é DERIVADA
 * por query (só significativa em 'aguardando'). */
export type WaitlistEntry = {
  id: string
  classId: string
  contactId: string | null
  studentName: string
  studentPhone: string | null
  status: WaitlistStatusId
  enqueuedAt: string
  position: number
}

/** Passe de day-use / aula avulsa (espelha AcademiaDayPass, migration 75). Cobrança real é #50. */
export type DayPass = {
  id: string
  contactId: string | null
  guestName: string
  guestPhone: string | null
  classId: string | null
  passDate: string // "YYYY-MM-DD"
  priceCents: number
  paid: boolean
  createdAt: string
}

/** Indicação do programa "indique um amigo" (espelha AcademiaReferral, migration 76). */
export type Referral = {
  id: string
  referrerContactId: string | null
  referredName: string
  referredPhone: string | null
  code: string
  status: 'pendente' | 'convertida' | 'expirada'
  rewardPercent: number | null
  createdAt: string
  convertedAt: string | null
}

/** Cupom de desconto (espelha AcademiaCoupon, migration 77). kind percent (1..100) | fixed (centavos). */
export type Coupon = {
  id: string
  companyId: string
  code: string
  kind: 'percent' | 'fixed'
  value: number
  minCents: number
  maxUses: number | null
  uses: number
  validUntil: string | null // "YYYY-MM-DD"
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Resultado do POST /coupons/validate. */
export type CouponValidation = {
  valid: boolean
  discountCents: number
  reason: string | null
  couponId: string | null
  code: string | null
}

/** Config de fidelidade por assiduidade (espelha AcademiaLoyaltyConfig, migration 78). */
export type LoyaltyConfig = {
  companyId: string
  enabled: boolean
  pointsPerCheckin: number
  rewardThreshold: number | null
  rewardText: string | null
}

/** Saldo de pontos de um contato (GET /loyalty/balance — inclui o limiar e se atingiu). */
export type LoyaltyBalanceView = {
  companyId: string
  contactId: string
  points: number
  updatedAt: string | null
  rewardThreshold: number | null
  rewardReached: boolean
}

/** Resumo gerencial (GET /reports/summary — chaves em snake_case, @JsonProperty do backend). */
export type SummaryReport = {
  mrr_cents: number
  active_count: number
  suspended_count: number
  canceled_count: number
}

/** Ocupação de uma aula (GET /reports/occupancy — chaves em snake_case). */
export type OccupancyRow = {
  class_id: string
  class_name: string
  day_of_week: number
  capacity: number
  active_count: number
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
