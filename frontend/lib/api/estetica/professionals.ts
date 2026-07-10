import { apiFetch } from '@/lib/api/client'
import type { AestheticProfessional } from '@/profiles/estetica/estetica-types'

export type CreateProfessionalInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}
export type UpdateProfessionalInput = Partial<CreateProfessionalInput> & { active?: boolean }

export function listProfessionals(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: AestheticProfessional[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: AestheticProfessional[] }>(`/api/estetica/professionals${qs}`)
}

export function createProfessional(input: CreateProfessionalInput): Promise<AestheticProfessional> {
  return apiFetch<AestheticProfessional>('/api/estetica/professionals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProfessional(
  id: string,
  input: UpdateProfessionalInput,
): Promise<AestheticProfessional> {
  return apiFetch<AestheticProfessional>(`/api/estetica/professionals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleProfessional(id: string, active: boolean): Promise<AestheticProfessional> {
  return apiFetch<AestheticProfessional>(`/api/estetica/professionals/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProfessional(id: string): Promise<void> {
  return apiFetch<void>(`/api/estetica/professionals/${id}`, { method: 'DELETE' })
}
