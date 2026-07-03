import { apiFetch } from '@/lib/api/client'
import type { OticaCategoryId } from '@/profiles/otica/otica-categories'
import type { CatalogItem, CatalogOption } from '@/profiles/otica/otica-types'

export type CreateCatalogItemInput = {
  name: string
  description?: string | null
  priceCents: number
  category: OticaCategoryId
  madeToOrder?: boolean
  leadTimeDays?: number | null
  available?: boolean
}

export type UpdateCatalogItemInput = Partial<CreateCatalogItemInput> & { available?: boolean }

export function listCatalog(
  opts: { category?: string; available?: boolean } = {},
): Promise<{ items: CatalogItem[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.available) p.set('available', 'true')
  const qs = p.toString()
  return apiFetch<{ items: CatalogItem[] }>(`/api/otica/catalog${qs ? `?${qs}` : ''}`)
}

export function getCatalogItem(id: string): Promise<CatalogItem> {
  return apiFetch<CatalogItem>(`/api/otica/catalog/${id}`)
}

export function createCatalogItem(input: CreateCatalogItemInput): Promise<CatalogItem> {
  return apiFetch<CatalogItem>('/api/otica/catalog', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCatalogItem(id: string, input: UpdateCatalogItemInput): Promise<CatalogItem> {
  return apiFetch<CatalogItem>(`/api/otica/catalog/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCatalogItem(id: string, available: boolean): Promise<CatalogItem> {
  return apiFetch<CatalogItem>(`/api/otica/catalog/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteCatalogItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/otica/catalog/${id}`, { method: 'DELETE' })
}

// ---- Opções/modifiers de um item (Tipo de lente / Tratamento) ----

export type CreateOptionInput = {
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  sortOrder?: number
}

export type UpdateOptionInput = Partial<CreateOptionInput> & { available?: boolean }

export function listOptions(itemId: string): Promise<{ items: CatalogOption[] }> {
  return apiFetch<{ items: CatalogOption[] }>(`/api/otica/catalog/${itemId}/options`)
}

export function createOption(itemId: string, input: CreateOptionInput): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`/api/otica/catalog/${itemId}/options`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOption(
  itemId: string,
  optionId: string,
  input: UpdateOptionInput,
): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`/api/otica/catalog/${itemId}/options/${optionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleOption(
  itemId: string,
  optionId: string,
  available: boolean,
): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`/api/otica/catalog/${itemId}/options/${optionId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteOption(itemId: string, optionId: string): Promise<void> {
  return apiFetch<void>(`/api/otica/catalog/${itemId}/options/${optionId}`, { method: 'DELETE' })
}
