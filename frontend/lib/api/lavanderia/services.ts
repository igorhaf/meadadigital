import { apiFetch } from '@/lib/api/client'
import type { LavanderiaCategoryId } from '@/profiles/lavanderia/lavanderia-categories'
import type { ServiceItem, ServiceOption } from '@/profiles/lavanderia/lavanderia-types'

export type CreateServiceInput = {
  name: string
  description?: string | null
  priceCents: number
  category: LavanderiaCategoryId
  turnaroundDays: number
  careInstructions?: string | null
  available?: boolean
}

export type UpdateServiceInput = Partial<CreateServiceInput> & { available?: boolean }

export function listServices(
  opts: { category?: string; available?: boolean } = {},
): Promise<{ items: ServiceItem[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.available) p.set('available', 'true')
  const qs = p.toString()
  return apiFetch<{ items: ServiceItem[] }>(`/api/lavanderia/services${qs ? `?${qs}` : ''}`)
}

export function getService(id: string): Promise<ServiceItem> {
  return apiFetch<ServiceItem>(`/api/lavanderia/services/${id}`)
}

export function createService(input: CreateServiceInput): Promise<ServiceItem> {
  return apiFetch<ServiceItem>('/api/lavanderia/services', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateService(id: string, input: UpdateServiceInput): Promise<ServiceItem> {
  return apiFetch<ServiceItem>(`/api/lavanderia/services/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleService(id: string, available: boolean): Promise<ServiceItem> {
  return apiFetch<ServiceItem>(`/api/lavanderia/services/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteService(id: string): Promise<void> {
  return apiFetch<void>(`/api/lavanderia/services/${id}`, { method: 'DELETE' })
}

// ---- Opções/modifiers de um serviço ----

export type CreateOptionInput = {
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  sortOrder?: number
}

export type UpdateOptionInput = Partial<CreateOptionInput> & { available?: boolean }

export function listOptions(serviceId: string): Promise<{ options: ServiceOption[] }> {
  return apiFetch<{ options: ServiceOption[] }>(`/api/lavanderia/services/${serviceId}/options`)
}

export function createOption(serviceId: string, input: CreateOptionInput): Promise<ServiceOption> {
  return apiFetch<ServiceOption>(`/api/lavanderia/services/${serviceId}/options`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOption(
  serviceId: string,
  optionId: string,
  input: UpdateOptionInput,
): Promise<ServiceOption> {
  return apiFetch<ServiceOption>(`/api/lavanderia/services/${serviceId}/options/${optionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleOption(
  serviceId: string,
  optionId: string,
  available: boolean,
): Promise<ServiceOption> {
  return apiFetch<ServiceOption>(
    `/api/lavanderia/services/${serviceId}/options/${optionId}/toggle`,
    {
      method: 'PATCH',
      body: JSON.stringify({ available }),
    },
  )
}

export function deleteOption(serviceId: string, optionId: string): Promise<void> {
  return apiFetch<void>(`/api/lavanderia/services/${serviceId}/options/${optionId}`, {
    method: 'DELETE',
  })
}
