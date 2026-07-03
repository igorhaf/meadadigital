import { apiFetch } from '@/lib/api/client'
import type { WeddingCatalogItem } from '@/profiles/casamento/casamento-types'

export type CreateCatalogItemInput = {
  name: string
  kind?: 'pacote' | 'adicional'
  description?: string | null
  priceCents: number
  active?: boolean
}
export type UpdateCatalogItemInput = Partial<CreateCatalogItemInput> & {
  clearDescription?: boolean
}

export function listCatalog(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: WeddingCatalogItem[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: WeddingCatalogItem[] }>(`/api/casamento/catalog${qs}`)
}

export function createCatalogItem(input: CreateCatalogItemInput): Promise<WeddingCatalogItem> {
  return apiFetch<WeddingCatalogItem>('/api/casamento/catalog', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCatalogItem(
  id: string,
  input: UpdateCatalogItemInput,
): Promise<WeddingCatalogItem> {
  return apiFetch<WeddingCatalogItem>(`/api/casamento/catalog/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteCatalogItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/catalog/${id}`, { method: 'DELETE' })
}
