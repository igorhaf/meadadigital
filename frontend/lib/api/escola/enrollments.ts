import { apiFetch } from '@/lib/api/client'
import type { EscolaEnrollmentStatusId } from '@/profiles/escola/escola-enrollment-status'
import type { EscolaEnrollment } from '@/profiles/escola/escola-types'

type EnrollmentPage = { items: EscolaEnrollment[]; total: number; page: number; pageSize: number }

export type CreateEnrollmentInput = {
  classId: string
  studentId: string
  notes?: string | null
}

export function listEnrollments(
  opts: { status?: string; classId?: string; studentId?: string; page?: number; pageSize?: number } = {},
): Promise<EnrollmentPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.classId) p.set('classId', opts.classId)
  if (opts.studentId) p.set('studentId', opts.studentId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<EnrollmentPage>(`/api/escola/enrollments${qs ? `?${qs}` : ''}`)
}

export function getEnrollment(id: string): Promise<EscolaEnrollment> {
  return apiFetch<EscolaEnrollment>(`/api/escola/enrollments/${id}`)
}

export function createEnrollment(input: CreateEnrollmentInput): Promise<EscolaEnrollment> {
  return apiFetch<EscolaEnrollment>('/api/escola/enrollments', { method: 'POST', body: JSON.stringify(input) })
}

export function updateEnrollmentStatus(id: string, newStatus: EscolaEnrollmentStatusId): Promise<EscolaEnrollment> {
  return apiFetch<EscolaEnrollment>(`/api/escola/enrollments/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}
