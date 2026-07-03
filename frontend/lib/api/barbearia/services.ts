import { apiFetch } from '@/lib/api/client'
import type { Service } from '@/profiles/barbearia/barber-types'

export type CreateServiceInput = {
  name: string
  category?: string | null
  durationMinutes: number
  priceCents?: number | null
  description?: string | null
}

export type UpdateServiceInput = Partial<CreateServiceInput> & { active?: boolean }

export function listServices(opts: { onlyActive?: boolean } = {}): Promise<{ items: Service[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Service[] }>(`/api/barbearia/services${qs}`)
}

export function getService(id: string): Promise<Service> {
  return apiFetch<Service>(`/api/barbearia/services/${id}`)
}

export function createService(input: CreateServiceInput): Promise<Service> {
  return apiFetch<Service>('/api/barbearia/services', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateService(id: string, input: UpdateServiceInput): Promise<Service> {
  return apiFetch<Service>(`/api/barbearia/services/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleService(id: string, active: boolean): Promise<Service> {
  return apiFetch<Service>(`/api/barbearia/services/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteService(id: string): Promise<void> {
  return apiFetch<void>(`/api/barbearia/services/${id}`, { method: 'DELETE' })
}
