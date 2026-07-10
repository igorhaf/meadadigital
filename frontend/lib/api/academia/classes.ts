import { apiFetch } from '@/lib/api/client'
import type { Class } from '@/profiles/academia/academia-types'

export type CreateClassInput = {
  name: string
  modality: string
  dayOfWeek: number
  startTime: string // "HH:MM"
  durationMinutes: number
  capacity: number
  instructor?: string | null
}

export type UpdateClassInput = Partial<CreateClassInput> & { active?: boolean }

export function listClasses(
  opts: { onlyActive?: boolean; dayOfWeek?: number } = {},
): Promise<{ items: Class[] }> {
  const p = new URLSearchParams()
  if (opts.onlyActive) p.set('onlyActive', 'true')
  if (opts.dayOfWeek !== undefined) p.set('dayOfWeek', String(opts.dayOfWeek))
  const qs = p.toString()
  return apiFetch<{ items: Class[] }>(`/api/academia/classes${qs ? `?${qs}` : ''}`)
}

export function createClass(input: CreateClassInput): Promise<Class> {
  return apiFetch<Class>('/api/academia/classes', { method: 'POST', body: JSON.stringify(input) })
}

export function updateClass(id: string, input: UpdateClassInput): Promise<Class> {
  return apiFetch<Class>(`/api/academia/classes/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleClass(id: string, active: boolean): Promise<Class> {
  return apiFetch<Class>(`/api/academia/classes/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteClass(id: string): Promise<void> {
  return apiFetch<void>(`/api/academia/classes/${id}`, { method: 'DELETE' })
}
