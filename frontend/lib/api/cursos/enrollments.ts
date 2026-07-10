import { apiFetch } from '@/lib/api/client'
import type { CursoEnrollmentStatusId } from '@/profiles/cursos/curso-enrollment-status'
import type { Enrollment } from '@/profiles/cursos/cursos-types'

type EnrollmentPage = { items: Enrollment[]; total: number; page: number; pageSize: number }

export type CreateEnrollmentInput = {
  courseId: string
  studentName: string
  studentPhone?: string | null
  notes?: string | null
}

export function listEnrollments(
  opts: { status?: string; courseId?: string; page?: number; pageSize?: number } = {},
): Promise<EnrollmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.courseId) p.set('courseId', opts.courseId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<EnrollmentPage>(`/api/cursos/enrollments${qs ? `?${qs}` : ''}`)
}

export function getEnrollment(id: string): Promise<Enrollment> {
  return apiFetch<Enrollment>(`/api/cursos/enrollments/${id}`)
}

export function createEnrollment(input: CreateEnrollmentInput): Promise<Enrollment> {
  return apiFetch<Enrollment>('/api/cursos/enrollments', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateEnrollmentStatus(
  id: string,
  newStatus: CursoEnrollmentStatusId,
): Promise<Enrollment> {
  return apiFetch<Enrollment>(`/api/cursos/enrollments/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
