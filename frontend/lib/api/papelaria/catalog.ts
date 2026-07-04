import { apiFetch } from '@/lib/api/client'
import type { PapelariaCategoryId } from '@/profiles/papelaria/papelaria-categories'
import type { CatalogItem, CatalogOption } from '@/profiles/papelaria/papelaria-types'

export type CreateCatalogItemInput = {
  name: string
  description?: string | null
  priceCents: number
  category: PapelariaCategoryId
  madeToOrder?: boolean
  leadTimeDays?: number | null
  specs?: string | null
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
  return apiFetch<{ items: CatalogItem[] }>(`/api/papelaria/catalog${qs ? `?${qs}` : ''}`)
}

export function getCatalogItem(id: string): Promise<CatalogItem> {
  return apiFetch<CatalogItem>(`/api/papelaria/catalog/${id}`)
}

export function createCatalogItem(input: CreateCatalogItemInput): Promise<CatalogItem> {
  return apiFetch<CatalogItem>('/api/papelaria/catalog', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCatalogItem(id: string, input: UpdateCatalogItemInput): Promise<CatalogItem> {
  return apiFetch<CatalogItem>(`/api/papelaria/catalog/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCatalogItem(id: string, available: boolean): Promise<CatalogItem> {
  return apiFetch<CatalogItem>(`/api/papelaria/catalog/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteCatalogItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/papelaria/catalog/${id}`, { method: 'DELETE' })
}

// ---- Opções/modifiers de um item (Papel/Acabamento/Cor/Tamanho) ----

export type CreateOptionInput = {
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  sortOrder?: number
}

export type UpdateOptionInput = Partial<CreateOptionInput> & { available?: boolean }

export function listOptions(itemId: string): Promise<{ items: CatalogOption[] }> {
  return apiFetch<{ items: CatalogOption[] }>(`/api/papelaria/catalog/${itemId}/options`)
}

export function createOption(itemId: string, input: CreateOptionInput): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`/api/papelaria/catalog/${itemId}/options`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOption(
  itemId: string,
  optionId: string,
  input: UpdateOptionInput,
): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`/api/papelaria/catalog/${itemId}/options/${optionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleOption(
  itemId: string,
  optionId: string,
  available: boolean,
): Promise<CatalogOption> {
  return apiFetch<CatalogOption>(`/api/papelaria/catalog/${itemId}/options/${optionId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteOption(itemId: string, optionId: string): Promise<void> {
  return apiFetch<void>(`/api/papelaria/catalog/${itemId}/options/${optionId}`, {
    method: 'DELETE',
  })
}

export type ItemTier = { minQty: number; unitPriceCents: number }

export function listTiers(itemId: string): Promise<{ items: ItemTier[] }> {
  return apiFetch<{ items: ItemTier[] }>(`/api/papelaria/catalog/${itemId}/tiers`)
}

/** Substitui TODAS as faixas de tiragem do item (onda #2). */
export function putTiers(itemId: string, tiers: ItemTier[]): Promise<{ items: ItemTier[] }> {
  return apiFetch<{ items: ItemTier[] }>(`/api/papelaria/catalog/${itemId}/tiers`, {
    method: 'PUT',
    body: JSON.stringify({ tiers }),
  })
}
