import type { FotografiaAppointmentStatusId } from './fotografia-appointment-status'

/** Profissional do estúdio (fotógrafo/cinegrafista). Espelha FotografiaProfessional. */
export type FotografiaProfessional = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do estúdio (espelha FotografiaConfig). opensAt/closesAt em "HH:MM:SS". slot em minutos. */
export type FotografiaConfig = {
  companyId: string
  opensAt: string
  closesAt: string
  slotMinutes: number
  reminderEnabled: boolean
  autoCompleteEnabled: boolean
  autoDeliverEnabled: boolean
  postDeliveryUpsellEnabled: boolean
  cancellationPolicyHours: number | null
}

/**
 * Pacote — a oferta do estúdio (espelha FotografiaPackage). category é texto livre (ensaio,
 * casamento, corporativo, vídeo…). durationMinutes = duração da sessão; deliveryDays = prazo de
 * entrega do material; priceCents = preço em centavos.
 */
export type FotografiaPackage = {
  id: string
  name: string
  category: string | null
  durationMinutes: number
  priceCents: number
  deliveryDays: number
  active: boolean
  suggestible: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Sessão (espelha FotografiaSession). startAt/endAt em ISO-8601 instant. O cliente é o CONTATO
 * direto (snapshots customerName/customerPhone — NÃO há sub-entidade de paciente). deliveryDueDate
 * é o prazo de entrega materializado (startAt + package.deliveryDays); deliveryLink é o link do
 * material entregue (escrito/editado pelo estúdio na agenda).
 */
export type FotografiaSession = {
  id: string
  professionalId: string
  professionalName: string
  packageId: string
  packageName: string
  customerName: string
  customerPhone: string | null
  contactId: string | null
  conversationId: string | null
  durationMinutes: number
  startAt: string
  endAt: string
  deliveryDueDate: string | null
  deliveryLink: string | null
  status: FotografiaAppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_slot (por profissional). */
export type ConflictDetail = {
  sessionId: string
  customerName: string
  startAt: string
  endAt: string
}

/** Formata centavos como R$ pt-BR. */
export function formatBrl(cents: number): string {
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
