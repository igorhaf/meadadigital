import { apiFetch } from '@/lib/api/client'
import type { PetProfessional } from '@/profiles/pet/pet-types'

export type CreateProfessionalInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateProfessionalInput = Partial<CreateProfessionalInput> & { active?: boolean }

export function listProfessionals(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: PetProfessional[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: PetProfessional[] }>(`/api/pet/professionals${qs}`)
}

export function getProfessional(id: string): Promise<PetProfessional> {
  return apiFetch<PetProfessional>(`/api/pet/professionals/${id}`)
}

export function createProfessional(input: CreateProfessionalInput): Promise<PetProfessional> {
  return apiFetch<PetProfessional>('/api/pet/professionals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProfessional(
  id: string,
  input: UpdateProfessionalInput,
): Promise<PetProfessional> {
  return apiFetch<PetProfessional>(`/api/pet/professionals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleProfessional(id: string, active: boolean): Promise<PetProfessional> {
  return apiFetch<PetProfessional>(`/api/pet/professionals/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProfessional(id: string): Promise<void> {
  return apiFetch<void>(`/api/pet/professionals/${id}`, { method: 'DELETE' })
}
