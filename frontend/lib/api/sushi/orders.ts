import { apiFetch } from '@/lib/api/client'
import type { Order } from '@/profiles/sushi/sushi-types'

type OrderPage = { items: Order[]; total: number; page: number; pageSize: number }

export function listOrders(
  opts: { status?: string; page?: number; pageSize?: number } = {},
): Promise<OrderPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<OrderPage>(`/api/sushi/orders${qs ? `?${qs}` : ''}`)
}

export function getOrder(id: string): Promise<Order> {
  return apiFetch<Order>(`/api/sushi/orders/${id}`)
}

/** Move o pedido para outro status (statusId = uuid de um status do tenant). */
export function updateOrderStatus(id: string, statusId: string): Promise<Order> {
  return apiFetch<Order>(`/api/sushi/orders/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: statusId }),
  })
}
