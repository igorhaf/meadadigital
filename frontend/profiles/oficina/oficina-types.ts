import type { OsStatusId } from './os-status'

/** Mecânico da oficina (espelha OsMechanic). */
export type OsMechanic = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Veículo — sub-entidade do cliente (espelha OsVehicle). */
export type OsVehicle = {
  id: string
  contactId: string
  plate: string
  brand: string | null
  model: string | null
  year: number | null
  color: string | null
  mileageKm: number | null
  notes: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Config da oficina (espelha OficinaConfig). opensAt/closesAt em "HH:MM:SS". */
export type OficinaConfig = {
  companyId: string
  opensAt: string
  closesAt: string
}

/** Tipo de item da OS. */
export type OsItemKind = 'peca' | 'mao_de_obra'

/** Item de uma OS (espelha OsItem). lineTotalCents materializado. */
export type OsItem = {
  id: string
  serviceOrderId: string
  kind: OsItemKind
  description: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
  createdAt: string
  updatedAt: string
}

/** Ordem de serviço (espelha ServiceOrder). totalCents materializado. items hidratado no detalhe. */
export type ServiceOrder = {
  id: string
  contactId: string | null
  vehicleId: string
  mechanicId: string | null
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  vehiclePlate: string
  vehicleModel: string | null
  mechanicName: string | null
  complaint: string
  diagnosis: string | null
  totalCents: number
  status: OsStatusId
  expectedDelivery: string | null
  notes: string | null
  openedAt: string
  closedAt: string | null
  statusUpdatedAt: string
  items: OsItem[]
}

const KIND_LABELS: Record<OsItemKind, string> = {
  peca: 'Peça',
  mao_de_obra: 'Mão de obra',
}
export function kindLabel(k: string | null | undefined): string {
  if (!k) return '—'
  return KIND_LABELS[k as OsItemKind] ?? k
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
