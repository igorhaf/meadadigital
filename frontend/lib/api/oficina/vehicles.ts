import { apiFetch } from '@/lib/api/client'
import type { OsVehicle } from '@/profiles/oficina/oficina-types'

export type CreateVehicleInput = {
  contactId: string
  plate: string
  brand?: string | null
  model?: string | null
  year?: number | null
  color?: string | null
  mileageKm?: number | null
  notes?: string | null
}

export type UpdateVehicleInput = {
  plate?: string
  brand?: string | null
  model?: string | null
  year?: number | null
  color?: string | null
  mileageKm?: number | null
  notes?: string | null
  active?: boolean
}

export function listVehicles(
  opts: { contactId?: string; active?: boolean; search?: string } = {},
): Promise<{ items: OsVehicle[] }> {
  const p = new URLSearchParams()
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.active !== undefined) p.set('active', String(opts.active))
  if (opts.search) p.set('search', opts.search)
  const qs = p.toString()
  return apiFetch<{ items: OsVehicle[] }>(`/api/oficina/vehicles${qs ? `?${qs}` : ''}`)
}

export function getVehicle(id: string): Promise<OsVehicle> {
  return apiFetch<OsVehicle>(`/api/oficina/vehicles/${id}`)
}

export function createVehicle(input: CreateVehicleInput): Promise<OsVehicle> {
  return apiFetch<OsVehicle>('/api/oficina/vehicles', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateVehicle(id: string, input: UpdateVehicleInput): Promise<OsVehicle> {
  return apiFetch<OsVehicle>(`/api/oficina/vehicles/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function archiveVehicle(id: string): Promise<OsVehicle> {
  return apiFetch<OsVehicle>(`/api/oficina/vehicles/${id}/archive`, { method: 'PATCH' })
}

export function deleteVehicle(id: string): Promise<void> {
  return apiFetch<void>(`/api/oficina/vehicles/${id}`, { method: 'DELETE' })
}
