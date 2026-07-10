import { apiFetch } from '@/lib/api/client'
import type { OficinaCatalogItem } from '@/profiles/oficina/oficina-types'

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
): Promise<{ items: OficinaCatalogItem[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: OficinaCatalogItem[] }>(`/api/oficina/catalog${qs}`)
}

export function createCatalogItem(input: CreateCatalogItemInput): Promise<OficinaCatalogItem> {
  return apiFetch<OficinaCatalogItem>('/api/oficina/catalog', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCatalogItem(
  id: string,
  input: UpdateCatalogItemInput,
): Promise<OficinaCatalogItem> {
  return apiFetch<OficinaCatalogItem>(`/api/oficina/catalog/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteCatalogItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/oficina/catalog/${id}`, { method: 'DELETE' })
}
