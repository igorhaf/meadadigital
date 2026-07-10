import { apiFetch } from '@/lib/api/client'
import type { PetService, PetSpecies } from '@/profiles/pet/pet-types'

export type CreateServiceInput = {
  name: string
  category?: string | null
  durationMinutes: number
  priceCents?: number | null
  speciesRestriction?: PetSpecies | null
  description?: string | null
}

/**
 * Update parcial. {@code speciesRestriction} é tri-estado no backend (JsonNode): ausente = não
 * mexe; null = limpa (vira "qualquer espécie"); valor = restringe. Só inclua a chave quando quiser
 * gravar — passar `undefined` a omite do JSON (JSON.stringify dropa undefined).
 */
export type UpdateServiceInput = Partial<CreateServiceInput> & { active?: boolean }

export function listServices(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: PetService[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: PetService[] }>(`/api/pet/services${qs}`)
}

export function getService(id: string): Promise<PetService> {
  return apiFetch<PetService>(`/api/pet/services/${id}`)
}

export function createService(input: CreateServiceInput): Promise<PetService> {
  return apiFetch<PetService>('/api/pet/services', { method: 'POST', body: JSON.stringify(input) })
}

export function updateService(id: string, input: UpdateServiceInput): Promise<PetService> {
  return apiFetch<PetService>(`/api/pet/services/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleService(id: string, active: boolean): Promise<PetService> {
  return apiFetch<PetService>(`/api/pet/services/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteService(id: string): Promise<void> {
  return apiFetch<void>(`/api/pet/services/${id}`, { method: 'DELETE' })
}
