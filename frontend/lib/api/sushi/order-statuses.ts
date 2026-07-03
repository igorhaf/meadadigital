import { apiFetch } from '@/lib/api/client'
import type { OrderStatusDef } from '@/profiles/sushi/sushi-types'

export type CreateOrderStatusInput = {
  name: string
  sortOrder?: number
  isInitial?: boolean
  isTerminal?: boolean
  notifyEnabled?: boolean
  notifyText?: string | null
  color?: string | null
}
export type UpdateOrderStatusInput = Partial<CreateOrderStatusInput>

export function listOrderStatuses(): Promise<{ items: OrderStatusDef[] }> {
  return apiFetch<{ items: OrderStatusDef[] }>('/api/sushi/order-statuses')
}

export function createOrderStatus(input: CreateOrderStatusInput): Promise<OrderStatusDef> {
  return apiFetch<OrderStatusDef>('/api/sushi/order-statuses', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOrderStatus(
  id: string,
  input: UpdateOrderStatusInput,
): Promise<OrderStatusDef> {
  return apiFetch<OrderStatusDef>(`/api/sushi/order-statuses/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteOrderStatus(id: string): Promise<void> {
  return apiFetch<void>(`/api/sushi/order-statuses/${id}`, { method: 'DELETE' })
}
