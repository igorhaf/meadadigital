import type { LeadStatusId } from './concessionaria-lead-status'
import type { TestDriveStatusId } from './concessionaria-test-drive-status'
import type { VehicleStatusId } from './concessionaria-vehicle-status'

/**
 * Tipos do perfil concessionaria (camada 8.17) — TRIPLE-HYBRID: ESTOQUE (veículos com ciclo de
 * status) + TEST-DRIVE (agenda) + LEAD (funil). Espelham os DTOs JSON (camelCase) do backend.
 */

/** Veículo do estoque (espelha ConcessionariaVehicle). priceCents obrigatório; demais nullables. */
export type Vehicle = {
  id: string
  companyId: string
  brand: string
  model: string
  modelYear: number | null
  mileageKm: number | null
  priceCents: number
  color: string | null
  fuel: string | null
  transmission: string | null
  plate: string | null
  photoUrl: string | null
  description: string | null
  status: VehicleStatusId
  active: boolean
  createdAt: string
  updatedAt: string
  statusUpdatedAt: string
}

/** Vendedor (espelha ConcessionariaSalesperson). */
export type Salesperson = {
  id: string
  companyId: string
  name: string
  phone: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Test-drive (espelha ConcessionariaTestDrive). snapshots de veículo; startAt/endAt ISO instant. */
export type TestDrive = {
  id: string
  companyId: string
  vehicleId: string
  salespersonId: string
  conversationId: string | null
  contactId: string | null
  customerName: string | null
  vehicleBrand: string
  vehicleModel: string
  vehicleYear: number | null
  startAt: string
  durationMinutes: number
  endAt: string
  status: TestDriveStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Condição de pagamento do lead. */
export type PaymentCondition = 'avista' | 'financiado'

/** Lead do funil de venda (espelha ConcessionariaLead). snapshots de veículo. */
export type Lead = {
  id: string
  companyId: string
  vehicleId: string
  conversationId: string | null
  contactId: string | null
  customerName: string | null
  customerPhone: string | null
  vehicleBrand: string
  vehicleModel: string
  vehicleYear: number | null
  vehiclePriceCents: number
  paymentCondition: PaymentCondition
  status: LeadStatusId
  salespersonId: string | null
  lostReason: string | null
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Config da loja (espelha ConcessionariaConfig). opensAt/closesAt em "HH:MM:SS". */
export type Config = {
  businessName: string | null
  durationMinutes: number
  bufferMinutes: number
  opensAt: string
  closesAt: string
  notes: string | null
  followupEnabled: boolean
  followupDays: number
  testdriveReminderEnabled: boolean
  autoCompleteEnabled: boolean
}

/** Desejo de carro (onda 1, backlog #1 — espelha ConcessionariaWishlist). ONE-SHOT ao notificar. */
export type ConcessionariaWishlist = {
  id: string
  companyId: string
  contactId: string
  conversationId: string | null
  contactName: string | null
  brand: string | null
  model: string | null
  maxPriceCents: number | null
  minYear: number | null
  notes: string | null
  active: boolean
  notifiedAt: string | null
  notifiedVehicleId: string | null
  createdAt: string
  updatedAt: string
}

/** Linhas agregadas do dashboard comercial (onda 1, backlog #10). */
export type ConcessionariaReportRow = {
  status?: string
  month?: string
  salesperson?: string
  count?: number
  totalCents?: number
  closedLeads?: number
  closedCents?: number
  testDrives?: number
}

export type ConcessionariaReportSummary = {
  months: number
  leadsCreated: number
  leadsClosed: number
  funnel: ConcessionariaReportRow[]
  bySalesperson: ConcessionariaReportRow[]
  salesByMonth: ConcessionariaReportRow[]
  testDrivesByStatus: ConcessionariaReportRow[]
}

/** Detalhe do conflito no 409 conflict_slot (test-drive). */
export type ConflictDetail = {
  testDriveId: string
  customerName: string | null
  startAt: string
  endAt: string
}

/** Sugestões de combustível (datalist do form — texto livre, não impõe). */
export const FUELS = ['Flex', 'Gasolina', 'Etanol', 'Diesel', 'Híbrido', 'Elétrico', 'GNV'] as const

/** Sugestões de câmbio (datalist do form — texto livre, não impõe). */
export const TRANSMISSIONS = ['Manual', 'Automático', 'Automatizado', 'CVT'] as const

/** Rótulos pt-BR das condições de pagamento. */
export const PAYMENT_CONDITIONS: { id: PaymentCondition; label: string }[] = [
  { id: 'avista', label: 'À vista' },
  { id: 'financiado', label: 'Financiado' },
]

/** Rótulo pt-BR de uma condição de pagamento (fallback: o próprio id). */
export function paymentConditionLabel(id: string): string {
  return PAYMENT_CONDITIONS.find((c) => c.id === id)?.label ?? id
}

/** Formata centavos em R$ pt-BR (— se null). */
export function formatBrl(cents: number | null | undefined): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
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
