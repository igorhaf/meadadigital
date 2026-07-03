import { apiFetch } from '@/lib/api/client'
import type { OsMechanic } from '@/profiles/oficina/oficina-types'

export type CreateMechanicInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateMechanicInput = Partial<CreateMechanicInput> & { active?: boolean }

export function listMechanics(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: OsMechanic[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: OsMechanic[] }>(`/api/oficina/mechanics${qs}`)
}

export function getMechanic(id: string): Promise<OsMechanic> {
  return apiFetch<OsMechanic>(`/api/oficina/mechanics/${id}`)
}

export function createMechanic(input: CreateMechanicInput): Promise<OsMechanic> {
  return apiFetch<OsMechanic>('/api/oficina/mechanics', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateMechanic(id: string, input: UpdateMechanicInput): Promise<OsMechanic> {
  return apiFetch<OsMechanic>(`/api/oficina/mechanics/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleMechanic(id: string, active: boolean): Promise<OsMechanic> {
  return apiFetch<OsMechanic>(`/api/oficina/mechanics/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteMechanic(id: string): Promise<void> {
  return apiFetch<void>(`/api/oficina/mechanics/${id}`, { method: 'DELETE' })
}
