import { apiFetch } from '@/lib/api/client'
import type { NutriProfessional } from '@/profiles/nutri/nutri-types'

export type CreateProfessionalInput = {
  name: string
  specialty?: string | null
  crn?: string | null
  notes?: string | null
}

export type UpdateProfessionalInput = Partial<CreateProfessionalInput> & { active?: boolean }

export function listProfessionals(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: NutriProfessional[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: NutriProfessional[] }>(`/api/nutri/professionals${qs}`)
}

export function getProfessional(id: string): Promise<NutriProfessional> {
  return apiFetch<NutriProfessional>(`/api/nutri/professionals/${id}`)
}

export function createProfessional(input: CreateProfessionalInput): Promise<NutriProfessional> {
  return apiFetch<NutriProfessional>('/api/nutri/professionals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProfessional(
  id: string,
  input: UpdateProfessionalInput,
): Promise<NutriProfessional> {
  return apiFetch<NutriProfessional>(`/api/nutri/professionals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleProfessional(id: string, active: boolean): Promise<NutriProfessional> {
  return apiFetch<NutriProfessional>(`/api/nutri/professionals/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProfessional(id: string): Promise<void> {
  return apiFetch<void>(`/api/nutri/professionals/${id}`, { method: 'DELETE' })
}
