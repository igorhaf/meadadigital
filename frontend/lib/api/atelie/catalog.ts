import { apiFetch } from '@/lib/api/client'
import type { AtelieCatalogItem } from '@/profiles/atelie/atelie-types'

export type CreateCatalogItemInput = {
  name: string
  category?: string | null
  unitPriceCents: number
  active?: boolean
  notes?: string | null
}
export type UpdateCatalogItemInput = Partial<CreateCatalogItemInput> & {
  clearCategory?: boolean
  clearNotes?: boolean
}

export function listCatalog(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: AtelieCatalogItem[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: AtelieCatalogItem[] }>(`/api/atelie/catalog${qs}`)
}

export function createCatalogItem(input: CreateCatalogItemInput): Promise<AtelieCatalogItem> {
  return apiFetch<AtelieCatalogItem>('/api/atelie/catalog', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCatalogItem(
  id: string,
  input: UpdateCatalogItemInput,
): Promise<AtelieCatalogItem> {
  return apiFetch<AtelieCatalogItem>(`/api/atelie/catalog/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteCatalogItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/atelie/catalog/${id}`, { method: 'DELETE' })
}
