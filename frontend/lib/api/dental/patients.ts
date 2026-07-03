import { apiFetch } from '@/lib/api/client'
import type { Patient } from '@/profiles/dental/dental-types'

export type CreatePatientInput = {
  name: string
  email?: string | null
  phone?: string | null
  document?: string | null
  birthDate?: string | null // "YYYY-MM-DD"
  notes?: string | null
}

export type UpdatePatientInput = Partial<CreatePatientInput>

export function listPatients(opts: { search?: string } = {}): Promise<{ items: Patient[] }> {
  const qs = opts.search ? `?search=${encodeURIComponent(opts.search)}` : ''
  return apiFetch<{ items: Patient[] }>(`/api/dental/patients${qs}`)
}

export function getPatient(id: string): Promise<Patient> {
  return apiFetch<Patient>(`/api/dental/patients/${id}`)
}

export function createPatient(input: CreatePatientInput): Promise<Patient> {
  return apiFetch<Patient>('/api/dental/patients', { method: 'POST', body: JSON.stringify(input) })
}

export function updatePatient(id: string, input: UpdatePatientInput): Promise<Patient> {
  return apiFetch<Patient>(`/api/dental/patients/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deletePatient(id: string): Promise<void> {
  return apiFetch<void>(`/api/dental/patients/${id}`, { method: 'DELETE' })
}
