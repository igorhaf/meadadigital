import { apiFetch } from '@/lib/api/client'
import type { Room } from '@/profiles/pousada/pousada-types'

export type CreateRoomInput = {
  name: string
  capacity: number
  nightlyRateCents: number
  description?: string | null
  notes?: string | null
}

export type UpdateRoomInput = Partial<CreateRoomInput> & { active?: boolean }

export function listRooms(opts: { onlyActive?: boolean } = {}): Promise<{ items: Room[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Room[] }>(`/api/pousada/rooms${qs}`)
}

export function getRoom(id: string): Promise<Room> {
  return apiFetch<Room>(`/api/pousada/rooms/${id}`)
}

export function createRoom(input: CreateRoomInput): Promise<Room> {
  return apiFetch<Room>('/api/pousada/rooms', { method: 'POST', body: JSON.stringify(input) })
}

export function updateRoom(id: string, input: UpdateRoomInput): Promise<Room> {
  return apiFetch<Room>(`/api/pousada/rooms/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleRoom(id: string, active: boolean): Promise<Room> {
  return apiFetch<Room>(`/api/pousada/rooms/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteRoom(id: string): Promise<void> {
  return apiFetch<void>(`/api/pousada/rooms/${id}`, { method: 'DELETE' })
}
