import { apiFetch } from '@/lib/api/client'
import type { NutriPatient } from '@/profiles/nutri/nutri-types'

export type CreatePatientInput = {
  contactId: string
  name: string
  goal?: string | null
  dietaryRestrictions?: string | null
  birthDate?: string | null // yyyy-MM-dd
  notes?: string | null
}

export type UpdatePatientInput = {
  name?: string
  goal?: string | null
  dietaryRestrictions?: string | null
  birthDate?: string | null
  clearBirthDate?: boolean
  notes?: string | null
  active?: boolean
}

export function listPatients(
  opts: { contactId?: string; active?: boolean; search?: string } = {},
): Promise<{ items: NutriPatient[] }> {
  const p = new URLSearchParams()
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.active !== undefined) p.set('active', String(opts.active))
  if (opts.search) p.set('search', opts.search)
  const qs = p.toString()
  return apiFetch<{ items: NutriPatient[] }>(`/api/nutri/patients${qs ? `?${qs}` : ''}`)
}

export function getPatient(id: string): Promise<NutriPatient> {
  return apiFetch<NutriPatient>(`/api/nutri/patients/${id}`)
}

export function createPatient(input: CreatePatientInput): Promise<NutriPatient> {
  return apiFetch<NutriPatient>('/api/nutri/patients', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePatient(id: string, input: UpdatePatientInput): Promise<NutriPatient> {
  return apiFetch<NutriPatient>(`/api/nutri/patients/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function archivePatient(id: string): Promise<NutriPatient> {
  return apiFetch<NutriPatient>(`/api/nutri/patients/${id}/archive`, { method: 'PATCH' })
}

export function deletePatient(id: string): Promise<void> {
  return apiFetch<void>(`/api/nutri/patients/${id}`, { method: 'DELETE' })
}
