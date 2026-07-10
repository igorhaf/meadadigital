import { apiFetch } from '@/lib/api/client'
import type { Table } from '@/profiles/restaurant/restaurant-types'

export type CreateTableInput = {
  label: string
  capacity: number
  notes?: string | null
}

export type UpdateTableInput = Partial<CreateTableInput> & { available?: boolean }

export function listTables(opts: { onlyAvailable?: boolean } = {}): Promise<{ items: Table[] }> {
  const qs = opts.onlyAvailable ? '?onlyAvailable=true' : ''
  return apiFetch<{ items: Table[] }>(`/api/restaurant/tables${qs}`)
}

export function getTable(id: string): Promise<Table> {
  return apiFetch<Table>(`/api/restaurant/tables/${id}`)
}

export function createTable(input: CreateTableInput): Promise<Table> {
  return apiFetch<Table>('/api/restaurant/tables', { method: 'POST', body: JSON.stringify(input) })
}

export function updateTable(id: string, input: UpdateTableInput): Promise<Table> {
  return apiFetch<Table>(`/api/restaurant/tables/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleTable(id: string, available: boolean): Promise<Table> {
  return apiFetch<Table>(`/api/restaurant/tables/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteTable(id: string): Promise<void> {
  return apiFetch<void>(`/api/restaurant/tables/${id}`, { method: 'DELETE' })
}
