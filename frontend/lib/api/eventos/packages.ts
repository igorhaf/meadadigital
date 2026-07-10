import { apiFetch } from '@/lib/api/client'
import type { EventPackage } from '@/profiles/eventos/eventos-types'

export type CreatePackageInput = {
  name: string
  kind?: string
  description?: string | null
  priceCents: number
  suggestible?: boolean
  active?: boolean
}
export type UpdatePackageInput = Partial<CreatePackageInput>

export function listPackages(): Promise<{ items: EventPackage[] }> {
  return apiFetch<{ items: EventPackage[] }>('/api/eventos/packages')
}

export function createPackage(input: CreatePackageInput): Promise<EventPackage> {
  return apiFetch<EventPackage>('/api/eventos/packages', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePackage(id: string, input: UpdatePackageInput): Promise<EventPackage> {
  return apiFetch<EventPackage>(`/api/eventos/packages/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deletePackage(id: string): Promise<void> {
  return apiFetch<void>(`/api/eventos/packages/${id}`, { method: 'DELETE' })
}

export function checkDate(
  date: string,
  excludeId?: string,
): Promise<{ occupied: boolean; count: number }> {
  const p = new URLSearchParams({ date })
  if (excludeId) p.set('excludeId', excludeId)
  return apiFetch<{ occupied: boolean; count: number }>(
    `/api/eventos/proposals/date-check?${p.toString()}`,
  )
}
