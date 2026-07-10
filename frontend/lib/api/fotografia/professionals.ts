import { apiFetch } from '@/lib/api/client'
import type { FotografiaProfessional } from '@/profiles/fotografia/fotografia-types'

export type CreateProfessionalInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateProfessionalInput = Partial<CreateProfessionalInput> & { active?: boolean }

export function listProfessionals(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: FotografiaProfessional[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: FotografiaProfessional[] }>(`/api/fotografia/professionals${qs}`)
}

export function getProfessional(id: string): Promise<FotografiaProfessional> {
  return apiFetch<FotografiaProfessional>(`/api/fotografia/professionals/${id}`)
}

export function createProfessional(
  input: CreateProfessionalInput,
): Promise<FotografiaProfessional> {
  return apiFetch<FotografiaProfessional>('/api/fotografia/professionals', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProfessional(
  id: string,
  input: UpdateProfessionalInput,
): Promise<FotografiaProfessional> {
  return apiFetch<FotografiaProfessional>(`/api/fotografia/professionals/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleProfessional(id: string, active: boolean): Promise<FotografiaProfessional> {
  return apiFetch<FotografiaProfessional>(`/api/fotografia/professionals/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProfessional(id: string): Promise<void> {
  return apiFetch<void>(`/api/fotografia/professionals/${id}`, { method: 'DELETE' })
}
