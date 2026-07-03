import { apiFetch } from '@/lib/api/client'
import type { Professional } from '@/profiles/salon/salon-types'

export type CreateProfessionalInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateProfessionalInput = Partial<CreateProfessionalInput> & { active?: boolean }

export function listProfessionals(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: Professional[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Professional[] }>(`/api/salon/professionals${qs}`)
}

export function getProfessional(id: string): Promise<Professional> {
  return apiFetch<Professional>(`/api/salon/professionals/${id}`)
}

export function createProfessional(input: CreateProfessionalInput): Promise<Professional> {
  return apiFetch<Professional>('/api/salon/professionals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProfessional(
  id: string,
  input: UpdateProfessionalInput,
): Promise<Professional> {
  return apiFetch<Professional>(`/api/salon/professionals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleProfessional(id: string, active: boolean): Promise<Professional> {
  return apiFetch<Professional>(`/api/salon/professionals/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProfessional(id: string): Promise<void> {
  return apiFetch<void>(`/api/salon/professionals/${id}`, { method: 'DELETE' })
}
