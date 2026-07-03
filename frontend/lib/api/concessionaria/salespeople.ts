import { apiFetch } from '@/lib/api/client'
import type { Salesperson } from '@/profiles/concessionaria/concessionaria-types'

export type CreateSalespersonInput = {
  name: string
  phone?: string | null
  notes?: string | null
}

export type UpdateSalespersonInput = Partial<CreateSalespersonInput> & { active?: boolean }

export function listSalespeople(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: Salesperson[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Salesperson[] }>(`/api/concessionaria/salespeople${qs}`)
}

export function getSalesperson(id: string): Promise<Salesperson> {
  return apiFetch<Salesperson>(`/api/concessionaria/salespeople/${id}`)
}

export function createSalesperson(input: CreateSalespersonInput): Promise<Salesperson> {
  return apiFetch<Salesperson>('/api/concessionaria/salespeople', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateSalesperson(id: string, input: UpdateSalespersonInput): Promise<Salesperson> {
  return apiFetch<Salesperson>(`/api/concessionaria/salespeople/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleSalesperson(id: string, active: boolean): Promise<Salesperson> {
  return apiFetch<Salesperson>(`/api/concessionaria/salespeople/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteSalesperson(id: string): Promise<void> {
  return apiFetch<void>(`/api/concessionaria/salespeople/${id}`, { method: 'DELETE' })
}
