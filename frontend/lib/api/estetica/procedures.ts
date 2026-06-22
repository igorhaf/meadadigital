import { apiFetch } from '@/lib/api/client'
import type { AestheticProcedure } from '@/profiles/estetica/estetica-types'

export type CreateProcedureInput = {
  name: string
  category?: string | null
  durationMinutes: number
  unitPriceCents: number
  notes?: string | null
}
export type UpdateProcedureInput = Partial<CreateProcedureInput> & { active?: boolean }

export function listProcedures(opts: { onlyActive?: boolean } = {}): Promise<{ items: AestheticProcedure[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: AestheticProcedure[] }>(`/api/estetica/procedures${qs}`)
}

export function createProcedure(input: CreateProcedureInput): Promise<AestheticProcedure> {
  return apiFetch<AestheticProcedure>('/api/estetica/procedures', { method: 'POST', body: JSON.stringify(input) })
}

export function updateProcedure(id: string, input: UpdateProcedureInput): Promise<AestheticProcedure> {
  return apiFetch<AestheticProcedure>(`/api/estetica/procedures/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleProcedure(id: string, active: boolean): Promise<AestheticProcedure> {
  return apiFetch<AestheticProcedure>(`/api/estetica/procedures/${id}/toggle`, {
    method: 'PATCH', body: JSON.stringify({ active }),
  })
}

export function deleteProcedure(id: string): Promise<void> {
  return apiFetch<void>(`/api/estetica/procedures/${id}`, { method: 'DELETE' })
}
