import { apiFetch } from '@/lib/api/client'
import type { DermatologiaProcedureType } from '@/profiles/dermatologia/dermatologia-types'

export type CreateProcedureTypeInput = {
  name: string
  durationMinutes: number
  prepInstructions?: string | null
  notes?: string | null
}

export type UpdateProcedureTypeInput = {
  name?: string
  durationMinutes?: number
  prepInstructions?: string | null
  clearPrep?: boolean
  notes?: string | null
  active?: boolean
}

export function listProcedureTypes(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: DermatologiaProcedureType[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: DermatologiaProcedureType[] }>(`/api/dermatologia/procedure-types${qs}`)
}

export function getProcedureType(id: string): Promise<DermatologiaProcedureType> {
  return apiFetch<DermatologiaProcedureType>(`/api/dermatologia/procedure-types/${id}`)
}

export function createProcedureType(
  input: CreateProcedureTypeInput,
): Promise<DermatologiaProcedureType> {
  return apiFetch<DermatologiaProcedureType>('/api/dermatologia/procedure-types', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateProcedureType(
  id: string,
  input: UpdateProcedureTypeInput,
): Promise<DermatologiaProcedureType> {
  return apiFetch<DermatologiaProcedureType>(`/api/dermatologia/procedure-types/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleProcedureType(
  id: string,
  active: boolean,
): Promise<DermatologiaProcedureType> {
  return apiFetch<DermatologiaProcedureType>(`/api/dermatologia/procedure-types/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProcedureType(id: string): Promise<void> {
  return apiFetch<void>(`/api/dermatologia/procedure-types/${id}`, { method: 'DELETE' })
}
