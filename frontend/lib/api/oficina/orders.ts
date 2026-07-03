import { apiFetch } from '@/lib/api/client'
import type { OsItem, OsItemKind, ServiceOrder } from '@/profiles/oficina/oficina-types'
import type { OsStatusId } from '@/profiles/oficina/os-status'

type OrderPage = { items: ServiceOrder[]; total: number; page: number; pageSize: number }

export type OpenOrderInput = {
  vehicleId: string
  mechanicId?: string | null
  complaint: string
  diagnosis?: string | null
  expectedDelivery?: string | null // yyyy-MM-dd
  notes?: string | null
}

export type UpdateOrderInput = {
  diagnosis?: string | null
  mechanicId?: string | null
  clearMechanic?: boolean
  expectedDelivery?: string | null
  clearExpected?: boolean
  notes?: string | null
}

export type AddItemInput = {
  kind: OsItemKind
  description: string
  quantity: number
  unitPriceCents: number
}

export type UpdateItemInput = Partial<AddItemInput>

export function listOrders(
  opts: {
    status?: string
    mechanicId?: string
    vehicleId?: string
    contactId?: string
    dateFrom?: string
    dateTo?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<OrderPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.mechanicId) p.set('mechanicId', opts.mechanicId)
  if (opts.vehicleId) p.set('vehicleId', opts.vehicleId)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<OrderPage>(`/api/oficina/orders${qs ? `?${qs}` : ''}`)
}

export function getOrder(id: string): Promise<ServiceOrder> {
  return apiFetch<ServiceOrder>(`/api/oficina/orders/${id}`)
}

export function openOrder(input: OpenOrderInput): Promise<ServiceOrder> {
  return apiFetch<ServiceOrder>('/api/oficina/orders', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOrder(id: string, input: UpdateOrderInput): Promise<ServiceOrder> {
  return apiFetch<ServiceOrder>(`/api/oficina/orders/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function addItem(orderId: string, input: AddItemInput): Promise<OsItem> {
  return apiFetch<OsItem>(`/api/oficina/orders/${orderId}/items`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateItem(
  orderId: string,
  itemId: string,
  input: UpdateItemInput,
): Promise<OsItem> {
  return apiFetch<OsItem>(`/api/oficina/orders/${orderId}/items/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteItem(orderId: string, itemId: string): Promise<void> {
  return apiFetch<void>(`/api/oficina/orders/${orderId}/items/${itemId}`, { method: 'DELETE' })
}

export function updateOrderStatus(id: string, newStatus: OsStatusId): Promise<ServiceOrder> {
  return apiFetch<ServiceOrder>(`/api/oficina/orders/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
