import { apiFetch } from '@/lib/api/client'
import type { ComidaDeliveryZone } from '@/profiles/comida/comida-types'

export type CreateZoneInput = { name: string; feeCents: number; active?: boolean }
export type UpdateZoneInput = Partial<CreateZoneInput>

export function listZones(): Promise<{ items: ComidaDeliveryZone[] }> {
  return apiFetch<{ items: ComidaDeliveryZone[] }>('/api/comida/zones')
}

export function createZone(input: CreateZoneInput): Promise<ComidaDeliveryZone> {
  return apiFetch<ComidaDeliveryZone>('/api/comida/zones', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateZone(id: string, input: UpdateZoneInput): Promise<ComidaDeliveryZone> {
  return apiFetch<ComidaDeliveryZone>(`/api/comida/zones/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteZone(id: string): Promise<void> {
  return apiFetch<void>(`/api/comida/zones/${id}`, { method: 'DELETE' })
}
