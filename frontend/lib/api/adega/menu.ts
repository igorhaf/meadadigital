import { apiFetch } from '@/lib/api/client'
import type { AdegaCategoryId } from '@/profiles/adega/adega-categories'
import type { MenuItem, MenuOption } from '@/profiles/adega/adega-types'

export type CreateMenuItemInput = {
  name: string
  description?: string | null
  priceCents: number
  category: AdegaCategoryId
  available?: boolean
}

export type UpdateMenuItemInput = Partial<CreateMenuItemInput> & { available?: boolean }

export function listMenu(
  opts: { category?: string; available?: boolean } = {},
): Promise<{ items: MenuItem[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.available) p.set('available', 'true')
  const qs = p.toString()
  return apiFetch<{ items: MenuItem[] }>(`/api/adega/menu${qs ? `?${qs}` : ''}`)
}

export function getMenuItem(id: string): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/adega/menu/${id}`)
}

export function createMenuItem(input: CreateMenuItemInput): Promise<MenuItem> {
  return apiFetch<MenuItem>('/api/adega/menu', { method: 'POST', body: JSON.stringify(input) })
}

export function updateMenuItem(id: string, input: UpdateMenuItemInput): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/adega/menu/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleMenuItem(id: string, available: boolean): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/adega/menu/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteMenuItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/adega/menu/${id}`, { method: 'DELETE' })
}

// ---- Opções/modifiers de um item (Volume / Temperatura) ----

export type CreateOptionInput = {
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  sortOrder?: number
}

export type UpdateOptionInput = Partial<CreateOptionInput> & { available?: boolean }

export function listOptions(itemId: string): Promise<{ items: MenuOption[] }> {
  return apiFetch<{ items: MenuOption[] }>(`/api/adega/menu/${itemId}/options`)
}

export function createOption(itemId: string, input: CreateOptionInput): Promise<MenuOption> {
  return apiFetch<MenuOption>(`/api/adega/menu/${itemId}/options`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOption(
  itemId: string,
  optionId: string,
  input: UpdateOptionInput,
): Promise<MenuOption> {
  return apiFetch<MenuOption>(`/api/adega/menu/${itemId}/options/${optionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleOption(
  itemId: string,
  optionId: string,
  available: boolean,
): Promise<MenuOption> {
  return apiFetch<MenuOption>(`/api/adega/menu/${itemId}/options/${optionId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteOption(itemId: string, optionId: string): Promise<void> {
  return apiFetch<void>(`/api/adega/menu/${itemId}/options/${optionId}`, { method: 'DELETE' })
}
