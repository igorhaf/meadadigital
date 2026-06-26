import { apiFetch } from '@/lib/api/client'
import type { Product, Variant } from '@/profiles/suplementos/suplementos-types'
import type { SuplementosCategoryId } from '@/profiles/suplementos/suplementos-categories'

export type CreateProductInput = {
  name: string
  brand?: string | null
  description?: string | null
  category: SuplementosCategoryId
  active?: boolean
}

export type UpdateProductInput = Partial<CreateProductInput> & { active?: boolean }

export function listProducts(
  opts: { category?: string; active?: boolean } = {},
): Promise<{ items: Product[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.active) p.set('active', 'true')
  const qs = p.toString()
  return apiFetch<{ items: Product[] }>(`/api/suplementos/products${qs ? `?${qs}` : ''}`)
}

export function getProduct(id: string): Promise<Product> {
  return apiFetch<Product>(`/api/suplementos/products/${id}`)
}

export function createProduct(input: CreateProductInput): Promise<Product> {
  return apiFetch<Product>('/api/suplementos/products', { method: 'POST', body: JSON.stringify(input) })
}

export function updateProduct(id: string, input: UpdateProductInput): Promise<Product> {
  return apiFetch<Product>(`/api/suplementos/products/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleProduct(id: string, active: boolean): Promise<Product> {
  return apiFetch<Product>(`/api/suplementos/products/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteProduct(id: string): Promise<void> {
  return apiFetch<void>(`/api/suplementos/products/${id}`, { method: 'DELETE' })
}

// ---- Variantes de um produto (grade sabor × tamanho com estoque) ----

export type CreateVariantInput = {
  flavor?: string | null
  sizeLabel: string
  sku?: string | null
  priceCents: number
  stockQuantity: number
  expiryDate?: string | null
}

export type UpdateVariantInput = Partial<CreateVariantInput> & { active?: boolean }

export function listVariants(productId: string): Promise<{ items: Variant[] }> {
  return apiFetch<{ items: Variant[] }>(`/api/suplementos/products/${productId}/variants`)
}

export function createVariant(productId: string, input: CreateVariantInput): Promise<Variant> {
  return apiFetch<Variant>(`/api/suplementos/products/${productId}/variants`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateVariant(
  productId: string,
  variantId: string,
  input: UpdateVariantInput,
): Promise<Variant> {
  return apiFetch<Variant>(`/api/suplementos/products/${productId}/variants/${variantId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleVariant(productId: string, variantId: string, active: boolean): Promise<Variant> {
  return apiFetch<Variant>(`/api/suplementos/products/${productId}/variants/${variantId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteVariant(productId: string, variantId: string): Promise<void> {
  return apiFetch<void>(`/api/suplementos/products/${productId}/variants/${variantId}`, { method: 'DELETE' })
}
