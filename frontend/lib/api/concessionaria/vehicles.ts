import { apiFetch } from '@/lib/api/client'
import type { Vehicle } from '@/profiles/concessionaria/concessionaria-types'
import type { VehicleStatusId } from '@/profiles/concessionaria/concessionaria-vehicle-status'

export type CreateVehicleInput = {
  brand: string
  model: string
  priceCents: number
  modelYear?: number | null
  mileageKm?: number | null
  color?: string | null
  fuel?: string | null
  transmission?: string | null
  plate?: string | null
  photoUrl?: string | null
  description?: string | null
}

export type UpdateVehicleInput = Partial<CreateVehicleInput> & { active?: boolean }

export function listVehicles(opts: { available?: boolean } = {}): Promise<{ items: Vehicle[] }> {
  const qs = opts.available ? '?available=true' : ''
  return apiFetch<{ items: Vehicle[] }>(`/api/concessionaria/vehicles${qs}`)
}

export function getVehicle(id: string): Promise<Vehicle> {
  return apiFetch<Vehicle>(`/api/concessionaria/vehicles/${id}`)
}

export function createVehicle(input: CreateVehicleInput): Promise<Vehicle> {
  return apiFetch<Vehicle>('/api/concessionaria/vehicles', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateVehicle(id: string, input: UpdateVehicleInput): Promise<Vehicle> {
  return apiFetch<Vehicle>(`/api/concessionaria/vehicles/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function updateVehicleStatus(id: string, newStatus: VehicleStatusId): Promise<Vehicle> {
  return apiFetch<Vehicle>(`/api/concessionaria/vehicles/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}

export function deleteVehicle(id: string): Promise<void> {
  return apiFetch<void>(`/api/concessionaria/vehicles/${id}`, { method: 'DELETE' })
}
