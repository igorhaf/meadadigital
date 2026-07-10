import { apiFetch } from '@/lib/api/client'
import type { Barber } from '@/profiles/barbearia/barber-types'

export type CreateBarberInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateBarberInput = Partial<CreateBarberInput> & { active?: boolean }

export function listBarbers(opts: { onlyActive?: boolean } = {}): Promise<{ items: Barber[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Barber[] }>(`/api/barbearia/barbers${qs}`)
}

export function getBarber(id: string): Promise<Barber> {
  return apiFetch<Barber>(`/api/barbearia/barbers/${id}`)
}

export function createBarber(input: CreateBarberInput): Promise<Barber> {
  return apiFetch<Barber>('/api/barbearia/barbers', { method: 'POST', body: JSON.stringify(input) })
}

export function updateBarber(id: string, input: UpdateBarberInput): Promise<Barber> {
  return apiFetch<Barber>(`/api/barbearia/barbers/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleBarber(id: string, active: boolean): Promise<Barber> {
  return apiFetch<Barber>(`/api/barbearia/barbers/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteBarber(id: string): Promise<void> {
  return apiFetch<void>(`/api/barbearia/barbers/${id}`, { method: 'DELETE' })
}
