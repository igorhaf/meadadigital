import { apiFetch } from '@/lib/api/client'
import type { MenuItem, MenuOption } from '@/profiles/padaria/padaria-types'
import type { PadariaCategoryId } from '@/profiles/padaria/padaria-categories'

export type CreateMenuItemInput = {
  name: string
  description?: string | null
  priceCents: number
  category: PadariaCategoryId
  madeToOrder?: boolean
  leadTimeDays?: number | null
  allergens?: string | null
  available?: boolean
}

export type UpdateMenuItemInput = Partial<CreateMenuItemInput> & { available?: boolean }

export function listMenu(opts: { category?: string; available?: boolean } = {}): Promise<{ items: MenuItem[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.available) p.set('available', 'true')
  const qs = p.toString()
  return apiFetch<{ items: MenuItem[] }>(`/api/padaria/menu${qs ? `?${qs}` : ''}`)
}

export function getMenuItem(id: string): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/padaria/menu/${id}`)
}

export function createMenuItem(input: CreateMenuItemInput): Promise<MenuItem> {
  return apiFetch<MenuItem>('/api/padaria/menu', { method: 'POST', body: JSON.stringify(input) })
}

export function updateMenuItem(id: string, input: UpdateMenuItemInput): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/padaria/menu/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleMenuItem(id: string, available: boolean): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/padaria/menu/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteMenuItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/padaria/menu/${id}`, { method: 'DELETE' })
}

// ---- Opções/modifiers de um item (Sabor/Recheio/Tamanho) ----

export type CreateOptionInput = {
  groupLabel: string
  optionLabel: string
  priceDeltaCents: number
  sortOrder?: number
}

export type UpdateOptionInput = Partial<CreateOptionInput> & { available?: boolean }

export function listOptions(itemId: string): Promise<{ items: MenuOption[] }> {
  return apiFetch<{ items: MenuOption[] }>(`/api/padaria/menu/${itemId}/options`)
}

export function createOption(itemId: string, input: CreateOptionInput): Promise<MenuOption> {
  return apiFetch<MenuOption>(`/api/padaria/menu/${itemId}/options`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOption(itemId: string, optionId: string, input: UpdateOptionInput): Promise<MenuOption> {
  return apiFetch<MenuOption>(`/api/padaria/menu/${itemId}/options/${optionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleOption(itemId: string, optionId: string, available: boolean): Promise<MenuOption> {
  return apiFetch<MenuOption>(`/api/padaria/menu/${itemId}/options/${optionId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteOption(itemId: string, optionId: string): Promise<void> {
  return apiFetch<void>(`/api/padaria/menu/${itemId}/options/${optionId}`, { method: 'DELETE' })
}
