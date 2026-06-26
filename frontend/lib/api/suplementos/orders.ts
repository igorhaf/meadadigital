import { apiFetch } from '@/lib/api/client'
import type { Order, OrderStatus } from '@/profiles/suplementos/suplementos-types'

type OrderPage = { items: Order[]; total: number; page: number; pageSize: number }

export function listOrders(
  opts: { status?: string; page?: number; pageSize?: number } = {},
): Promise<OrderPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<OrderPage>(`/api/suplementos/orders${qs ? `?${qs}` : ''}`)
}

export function getOrder(id: string): Promise<Order> {
  return apiFetch<Order>(`/api/suplementos/orders/${id}`)
}

/**
 * Muda o status de um pedido. rejectionReason é opcional e só faz sentido com newStatus =
 * 'recusado' (gate de aceite); para os demais status fica undefined e o backend ignora.
 */
export function updateOrderStatus(
  id: string,
  newStatus: OrderStatus,
  rejectionReason?: string,
): Promise<Order> {
  return apiFetch<Order>(`/api/suplementos/orders/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus, rejectionReason }),
  })
}
