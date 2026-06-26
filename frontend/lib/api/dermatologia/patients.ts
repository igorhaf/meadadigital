import { apiFetch } from '@/lib/api/client'
import type { DermatologiaPatient } from '@/profiles/dermatologia/dermatologia-types'

export type CreatePatientInput = {
  contactId: string
  name: string
  birthDate?: string | null // yyyy-MM-dd
  notes?: string | null
}

export type UpdatePatientInput = {
  name?: string
  birthDate?: string | null
  clearBirthDate?: boolean
  notes?: string | null
  active?: boolean
}

export function listPatients(
  opts: { contactId?: string; active?: boolean; search?: string } = {},
): Promise<{ items: DermatologiaPatient[] }> {
  const p = new URLSearchParams()
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.active !== undefined) p.set('active', String(opts.active))
  if (opts.search) p.set('search', opts.search)
  const qs = p.toString()
  return apiFetch<{ items: DermatologiaPatient[] }>(`/api/dermatologia/patients${qs ? `?${qs}` : ''}`)
}

export function getPatient(id: string): Promise<DermatologiaPatient> {
  return apiFetch<DermatologiaPatient>(`/api/dermatologia/patients/${id}`)
}

export function createPatient(input: CreatePatientInput): Promise<DermatologiaPatient> {
  return apiFetch<DermatologiaPatient>('/api/dermatologia/patients', { method: 'POST', body: JSON.stringify(input) })
}

export function updatePatient(id: string, input: UpdatePatientInput): Promise<DermatologiaPatient> {
  return apiFetch<DermatologiaPatient>(`/api/dermatologia/patients/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function archivePatient(id: string): Promise<DermatologiaPatient> {
  return apiFetch<DermatologiaPatient>(`/api/dermatologia/patients/${id}/archive`, { method: 'PATCH' })
}

export function deletePatient(id: string): Promise<void> {
  return apiFetch<void>(`/api/dermatologia/patients/${id}`, { method: 'DELETE' })
}
