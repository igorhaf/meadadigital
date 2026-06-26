import { apiFetch } from '@/lib/api/client'
import type { Consultant } from '@/profiles/viagens/viagens-types'

export type CreateConsultantInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateConsultantInput = Partial<CreateConsultantInput> & { active?: boolean }

export function listConsultants(opts: { onlyActive?: boolean } = {}): Promise<{ items: Consultant[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Consultant[] }>(`/api/viagens/consultants${qs}`)
}

export function getConsultant(id: string): Promise<Consultant> {
  return apiFetch<Consultant>(`/api/viagens/consultants/${id}`)
}

export function createConsultant(input: CreateConsultantInput): Promise<Consultant> {
  return apiFetch<Consultant>('/api/viagens/consultants', { method: 'POST', body: JSON.stringify(input) })
}

export function updateConsultant(id: string, input: UpdateConsultantInput): Promise<Consultant> {
  return apiFetch<Consultant>(`/api/viagens/consultants/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleConsultant(id: string, active: boolean): Promise<Consultant> {
  return apiFetch<Consultant>(`/api/viagens/consultants/${id}/toggle`, {
    method: 'PATCH', body: JSON.stringify({ active }),
  })
}

export function deleteConsultant(id: string): Promise<void> {
  return apiFetch<void>(`/api/viagens/consultants/${id}`, { method: 'DELETE' })
}
