import { apiFetch } from '@/lib/api/client'
import type { OticaExamStatusId } from '@/profiles/otica/otica-exam-status'
import type { ExamAppointment } from '@/profiles/otica/otica-types'

type ExamPage = { items: ExamAppointment[]; total: number; page: number; pageSize: number }

export type CreateExamInput = {
  professionalId: string
  customerName: string
  startAt: string // ISO-8601 instant
  notes?: string | null
}

export function listExams(
  opts: {
    status?: string
    dateFrom?: string
    dateTo?: string
    professionalId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<ExamPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.dateFrom) p.set('dateFrom', opts.dateFrom)
  if (opts.dateTo) p.set('dateTo', opts.dateTo)
  if (opts.professionalId) p.set('professionalId', opts.professionalId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<ExamPage>(`/api/otica/exams${qs ? `?${qs}` : ''}`)
}

export function getExam(id: string): Promise<ExamAppointment> {
  return apiFetch<ExamAppointment>(`/api/otica/exams/${id}`)
}

export function createExam(input: CreateExamInput): Promise<ExamAppointment> {
  return apiFetch<ExamAppointment>('/api/otica/exams', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateExamStatus(
  id: string,
  newStatus: OticaExamStatusId,
): Promise<ExamAppointment> {
  return apiFetch<ExamAppointment>(`/api/otica/exams/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
