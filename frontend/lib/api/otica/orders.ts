import { apiFetch } from '@/lib/api/client'
import type { OticaOrderStatusId } from '@/profiles/otica/otica-order-status'
import type { Order } from '@/profiles/otica/otica-types'

type OrderPage = { items: Order[]; total: number; page: number; pageSize: number }

export function listOrders(
  opts: { status?: string; page?: number; pageSize?: number } = {},
): Promise<OrderPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<OrderPage>(`/api/otica/orders${qs ? `?${qs}` : ''}`)
}

export function getOrder(id: string): Promise<Order> {
  return apiFetch<Order>(`/api/otica/orders/${id}`)
}

/**
 * Muda o status de um pedido. rejectionReason é opcional e só faz sentido com newStatus =
 * 'recusado' (gate de aceite humano); para os demais status fica undefined e o backend ignora.
 */
export function updateOrderStatus(
  id: string,
  newStatus: OticaOrderStatusId,
  rejectionReason?: string,
): Promise<Order> {
  return apiFetch<Order>(`/api/otica/orders/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus, rejectionReason }),
  })
}
