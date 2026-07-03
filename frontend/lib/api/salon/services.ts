import { apiFetch } from '@/lib/api/client'
import type { Offering } from '@/profiles/salon/salon-types'

export type CreateOfferingInput = {
  name: string
  category?: string | null
  durationMinutes: number
  priceCents?: number | null
  description?: string | null
}

export type UpdateOfferingInput = Partial<CreateOfferingInput> & { active?: boolean }

export function listServices(opts: { onlyActive?: boolean } = {}): Promise<{ items: Offering[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Offering[] }>(`/api/salon/services${qs}`)
}

export function getService(id: string): Promise<Offering> {
  return apiFetch<Offering>(`/api/salon/services/${id}`)
}

export function createService(input: CreateOfferingInput): Promise<Offering> {
  return apiFetch<Offering>('/api/salon/services', { method: 'POST', body: JSON.stringify(input) })
}

export function updateService(id: string, input: UpdateOfferingInput): Promise<Offering> {
  return apiFetch<Offering>(`/api/salon/services/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleService(id: string, active: boolean): Promise<Offering> {
  return apiFetch<Offering>(`/api/salon/services/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteService(id: string): Promise<void> {
  return apiFetch<void>(`/api/salon/services/${id}`, { method: 'DELETE' })
}
