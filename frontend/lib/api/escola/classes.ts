import { apiFetch } from '@/lib/api/client'
import type { EscolaClass, EscolaShift } from '@/profiles/escola/escola-types'

export type CreateClassInput = {
  name: string
  grade: string
  shift: EscolaShift
  capacity: number
  monthlyCents: number
  year?: number | null
  description?: string | null
}

export type UpdateClassInput = Partial<CreateClassInput> & { active?: boolean }

export function listClasses(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: EscolaClass[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: EscolaClass[] }>(`/api/escola/classes${qs}`)
}

export function createClass(input: CreateClassInput): Promise<EscolaClass> {
  return apiFetch<EscolaClass>('/api/escola/classes', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateClass(id: string, input: UpdateClassInput): Promise<EscolaClass> {
  return apiFetch<EscolaClass>(`/api/escola/classes/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleClass(id: string, active: boolean): Promise<EscolaClass> {
  return apiFetch<EscolaClass>(`/api/escola/classes/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteClass(id: string): Promise<void> {
  return apiFetch<void>(`/api/escola/classes/${id}`, { method: 'DELETE' })
}
