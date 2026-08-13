import { apiFetch } from '@/lib/api/client'
import type { Category } from '@/profiles/sushi/sushi-types'

export type CreateCategoryInput = { name: string; sortOrder?: number; active?: boolean }
export type UpdateCategoryInput = Partial<CreateCategoryInput>

export function listCategories(): Promise<{ items: Category[] }> {
  return apiFetch<{ items: Category[] }>('/api/sushi/categories')
}

export function createCategory(input: CreateCategoryInput): Promise<Category> {
  return apiFetch<Category>('/api/sushi/categories', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCategory(id: string, input: UpdateCategoryInput): Promise<Category> {
  return apiFetch<Category>(`/api/sushi/categories/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleCategory(id: string): Promise<Category> {
  return apiFetch<Category>(`/api/sushi/categories/${id}/toggle`, { method: 'PATCH' })
}

export function deleteCategory(id: string): Promise<void> {
  return apiFetch<void>(`/api/sushi/categories/${id}`, { method: 'DELETE' })
}
