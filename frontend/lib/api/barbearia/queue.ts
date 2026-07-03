import { apiFetch } from '@/lib/api/client'
import type { BarberQueueStatusId } from '@/profiles/barbearia/barber-queue-status'
import type { QueueTicket } from '@/profiles/barbearia/barber-types'

type QueueResponse = { items: QueueTicket[]; waiting: number }

export type EnqueueInput = {
  barberId?: string | null // null = qualquer barbeiro
  serviceId: string
  guestName: string
  guestPhone?: string | null
  notes?: string | null
}

export function listQueue(): Promise<QueueResponse> {
  return apiFetch<QueueResponse>('/api/barbearia/queue')
}

export function getTicket(id: string): Promise<QueueTicket> {
  return apiFetch<QueueTicket>(`/api/barbearia/queue/${id}`)
}

export function enqueue(input: EnqueueInput): Promise<QueueTicket> {
  return apiFetch<QueueTicket>('/api/barbearia/queue', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateTicketStatus(
  id: string,
  newStatus: BarberQueueStatusId,
): Promise<QueueTicket> {
  return apiFetch<QueueTicket>(`/api/barbearia/queue/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
