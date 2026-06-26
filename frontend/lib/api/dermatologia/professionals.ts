import { apiFetch } from '@/lib/api/client'
import type { DermatologiaProfessional } from '@/profiles/dermatologia/dermatologia-types'

export type CreateProfessionalInput = {
  name: string
  specialty?: string | null
  crmRqe?: string | null
  notes?: string | null
}

export type UpdateProfessionalInput = Partial<CreateProfessionalInput> & { active?: boolean }

export function listProfessionals(opts: { onlyActive?: boolean } = {}): Promise<{ items: DermatologiaProfessional[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: DermatologiaProfessional[] }>(`/api/dermatologia/professionals${qs}`)
}

export function getProfessional(id: string): Promise<DermatologiaProfessional> {
  return apiFetch<DermatologiaProfessional>(`/api/dermatologia/professionals/${id}`)
}

export function createProfessional(input: CreateProfessionalInput): Promise<DermatologiaProfessional> {
  return apiFetch<DermatologiaProfessional>('/api/dermatologia/professionals', { method: 'POST', body: JSON.stringify(input) })
}

export function updateProfessional(id: string, input: UpdateProfessionalInput): Promise<DermatologiaProfessional> {
  return apiFetch<DermatologiaProfessional>(`/api/dermatologia/professionals/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleProfessional(id: string, active: boolean): Promise<DermatologiaProfessional> {
  return apiFetch<DermatologiaProfessional>(`/api/dermatologia/professionals/${id}/toggle`, {
    method: 'PATCH', body: JSON.stringify({ active }),
  })
}

export function deleteProfessional(id: string): Promise<void> {
  return apiFetch<void>(`/api/dermatologia/professionals/${id}`, { method: 'DELETE' })
}
