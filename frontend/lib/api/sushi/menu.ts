import { apiFetch } from '@/lib/api/client'
import type { MenuItem } from '@/profiles/sushi/sushi-types'
import type { SushiCategoryId } from '@/profiles/sushi/sushi-categories'

export type CreateMenuItemInput = {
  name: string
  description?: string | null
  priceCents: number
  category: SushiCategoryId
}

export type UpdateMenuItemInput = Partial<CreateMenuItemInput> & { available?: boolean }

export function listMenu(opts: { category?: string; available?: boolean } = {}): Promise<{ items: MenuItem[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.available) p.set('available', 'true')
  const qs = p.toString()
  return apiFetch<{ items: MenuItem[] }>(`/api/sushi/menu${qs ? `?${qs}` : ''}`)
}

export function getMenuItem(id: string): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/sushi/menu/${id}`)
}

export function createMenuItem(input: CreateMenuItemInput): Promise<MenuItem> {
  return apiFetch<MenuItem>('/api/sushi/menu', { method: 'POST', body: JSON.stringify(input) })
}

export function updateMenuItem(id: string, input: UpdateMenuItemInput): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/sushi/menu/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleMenuItem(id: string, available: boolean): Promise<MenuItem> {
  return apiFetch<MenuItem>(`/api/sushi/menu/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteMenuItem(id: string): Promise<void> {
  return apiFetch<void>(`/api/sushi/menu/${id}`, { method: 'DELETE' })
}
