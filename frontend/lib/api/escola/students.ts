import { apiFetch } from '@/lib/api/client'
import type { EscolaStudent } from '@/profiles/escola/escola-types'

export type CreateStudentInput = {
  contactId: string
  name: string
  birthDate?: string | null
  intendedGrade?: string | null
  notes?: string | null
}

export type UpdateStudentInput = {
  name?: string
  birthDate?: string | null
  intendedGrade?: string | null
  notes?: string | null
  active?: boolean
}

export function listStudents(
  opts: { contactId?: string; active?: boolean; search?: string } = {},
): Promise<{ items: EscolaStudent[] }> {
  const p = new URLSearchParams()
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.active !== undefined) p.set('active', String(opts.active))
  if (opts.search) p.set('search', opts.search)
  const qs = p.toString()
  return apiFetch<{ items: EscolaStudent[] }>(`/api/escola/students${qs ? `?${qs}` : ''}`)
}

export function getStudent(id: string): Promise<EscolaStudent> {
  return apiFetch<EscolaStudent>(`/api/escola/students/${id}`)
}

export function createStudent(input: CreateStudentInput): Promise<EscolaStudent> {
  return apiFetch<EscolaStudent>('/api/escola/students', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateStudent(id: string, input: UpdateStudentInput): Promise<EscolaStudent> {
  return apiFetch<EscolaStudent>(`/api/escola/students/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteStudent(id: string): Promise<void> {
  return apiFetch<void>(`/api/escola/students/${id}`, { method: 'DELETE' })
}
