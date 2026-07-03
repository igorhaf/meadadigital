import { apiFetch } from '@/lib/api/client'
import type { FotografiaPackage } from '@/profiles/fotografia/fotografia-types'

export type CreatePackageInput = {
  name: string
  category?: string | null
  durationMinutes: number
  priceCents: number
  deliveryDays: number
  notes?: string | null
}

export type UpdatePackageInput = {
  name?: string
  category?: string | null
  durationMinutes?: number
  priceCents?: number
  deliveryDays?: number
  notes?: string | null
  active?: boolean
}

export function listPackages(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: FotografiaPackage[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: FotografiaPackage[] }>(`/api/fotografia/packages${qs}`)
}

export function getPackage(id: string): Promise<FotografiaPackage> {
  return apiFetch<FotografiaPackage>(`/api/fotografia/packages/${id}`)
}

export function createPackage(input: CreatePackageInput): Promise<FotografiaPackage> {
  return apiFetch<FotografiaPackage>('/api/fotografia/packages', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePackage(id: string, input: UpdatePackageInput): Promise<FotografiaPackage> {
  return apiFetch<FotografiaPackage>(`/api/fotografia/packages/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function togglePackage(id: string, active: boolean): Promise<FotografiaPackage> {
  return apiFetch<FotografiaPackage>(`/api/fotografia/packages/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deletePackage(id: string): Promise<void> {
  return apiFetch<void>(`/api/fotografia/packages/${id}`, { method: 'DELETE' })
}
