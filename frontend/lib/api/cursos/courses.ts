import { apiFetch } from '@/lib/api/client'
import type { Course } from '@/profiles/cursos/cursos-types'

export type CreateCourseInput = {
  title: string
  category: string
  monthlyCents: number
  description?: string | null
}

export type UpdateCourseInput = Partial<CreateCourseInput> & { active?: boolean }

export function listCourses(opts: { onlyActive?: boolean } = {}): Promise<{ items: Course[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Course[] }>(`/api/cursos/courses${qs}`)
}

export function createCourse(input: CreateCourseInput): Promise<Course> {
  return apiFetch<Course>('/api/cursos/courses', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCourse(id: string, input: UpdateCourseInput): Promise<Course> {
  return apiFetch<Course>(`/api/cursos/courses/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCourse(id: string, active: boolean): Promise<Course> {
  return apiFetch<Course>(`/api/cursos/courses/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteCourse(id: string): Promise<void> {
  return apiFetch<void>(`/api/cursos/courses/${id}`, { method: 'DELETE' })
}
