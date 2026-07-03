import { apiFetch } from '@/lib/api/client'
import type { LegalClient } from '@/profiles/legal/legal-types'

export type CreateClientInput = {
  name: string
  email?: string | null
  phone?: string | null
  document?: string | null
  contactId?: string | null
  notes?: string | null
}

export type UpdateClientInput = Partial<CreateClientInput>

export function listClients(search?: string): Promise<{ items: LegalClient[] }> {
  const q = search ? `?search=${encodeURIComponent(search)}` : ''
  return apiFetch<{ items: LegalClient[] }>(`/api/legal/clients${q}`)
}

export function getClient(id: string): Promise<LegalClient> {
  return apiFetch<LegalClient>(`/api/legal/clients/${id}`)
}

export function createClient(input: CreateClientInput): Promise<LegalClient> {
  return apiFetch<LegalClient>('/api/legal/clients', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateClient(id: string, input: UpdateClientInput): Promise<LegalClient> {
  return apiFetch<LegalClient>(`/api/legal/clients/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteClient(id: string): Promise<void> {
  return apiFetch<void>(`/api/legal/clients/${id}`, { method: 'DELETE' })
}
