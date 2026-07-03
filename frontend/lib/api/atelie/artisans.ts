import { apiFetch } from '@/lib/api/client'
import type { AtelieArtisan } from '@/profiles/atelie/atelie-types'

export type CreateArtisanInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdateArtisanInput = Partial<CreateArtisanInput> & { active?: boolean }

export function listArtisans(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: AtelieArtisan[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: AtelieArtisan[] }>(`/api/atelie/artisans${qs}`)
}

export function getArtisan(id: string): Promise<AtelieArtisan> {
  return apiFetch<AtelieArtisan>(`/api/atelie/artisans/${id}`)
}

export function createArtisan(input: CreateArtisanInput): Promise<AtelieArtisan> {
  return apiFetch<AtelieArtisan>('/api/atelie/artisans', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateArtisan(id: string, input: UpdateArtisanInput): Promise<AtelieArtisan> {
  return apiFetch<AtelieArtisan>(`/api/atelie/artisans/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleArtisan(id: string, active: boolean): Promise<AtelieArtisan> {
  return apiFetch<AtelieArtisan>(`/api/atelie/artisans/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteArtisan(id: string): Promise<void> {
  return apiFetch<void>(`/api/atelie/artisans/${id}`, { method: 'DELETE' })
}
