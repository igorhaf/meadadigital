import { apiFetch } from '@/lib/api/client'
import type { PetAnimal, PetSex, PetSpecies } from '@/profiles/pet/pet-types'

export type CreateAnimalInput = {
  contactId: string
  name: string
  species: PetSpecies
  breed?: string | null
  sex?: PetSex | null
  birthYear?: number | null
  notes?: string | null
}

export type UpdateAnimalInput = {
  name?: string
  species?: PetSpecies
  breed?: string | null
  sex?: PetSex | null
  birthYear?: number | null
  notes?: string | null
  active?: boolean
}

export function listAnimals(
  opts: { contactId?: string; species?: string; active?: boolean; search?: string } = {},
): Promise<{ items: PetAnimal[] }> {
  const p = new URLSearchParams()
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.species) p.set('species', opts.species)
  if (opts.active !== undefined) p.set('active', String(opts.active))
  if (opts.search) p.set('search', opts.search)
  const qs = p.toString()
  return apiFetch<{ items: PetAnimal[] }>(`/api/pet/animals${qs ? `?${qs}` : ''}`)
}

export function getAnimal(id: string): Promise<PetAnimal> {
  return apiFetch<PetAnimal>(`/api/pet/animals/${id}`)
}

export function createAnimal(input: CreateAnimalInput): Promise<PetAnimal> {
  return apiFetch<PetAnimal>('/api/pet/animals', { method: 'POST', body: JSON.stringify(input) })
}

export function updateAnimal(id: string, input: UpdateAnimalInput): Promise<PetAnimal> {
  return apiFetch<PetAnimal>(`/api/pet/animals/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function archiveAnimal(id: string): Promise<PetAnimal> {
  return apiFetch<PetAnimal>(`/api/pet/animals/${id}/archive`, { method: 'PATCH' })
}

export function deleteAnimal(id: string): Promise<void> {
  return apiFetch<void>(`/api/pet/animals/${id}`, { method: 'DELETE' })
}
