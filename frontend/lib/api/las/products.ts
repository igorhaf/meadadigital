import { apiFetch } from '@/lib/api/client'
import type { Product, Variant } from '@/profiles/las/las-types'
import type { LasCategoryId } from '@/profiles/las/las-categories'

export type CreateProductInput = {
  name: string
  description?: string | null
  basePriceCents: number
  category: LasCategoryId
  available?: boolean
}

export type UpdateProductInput = Partial<CreateProductInput> & { available?: boolean }

export function listProducts(
  opts: { category?: string; available?: boolean } = {},
): Promise<{ items: Product[] }> {
  const p = new URLSearchParams()
  if (opts.category) p.set('category', opts.category)
  if (opts.available) p.set('available', 'true')
  const qs = p.toString()
  return apiFetch<{ items: Product[] }>(`/api/las/products${qs ? `?${qs}` : ''}`)
}

export function getProduct(id: string): Promise<Product> {
  return apiFetch<Product>(`/api/las/products/${id}`)
}

export function createProduct(input: CreateProductInput): Promise<Product> {
  return apiFetch<Product>('/api/las/products', { method: 'POST', body: JSON.stringify(input) })
}

export function updateProduct(id: string, input: UpdateProductInput): Promise<Product> {
  return apiFetch<Product>(`/api/las/products/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function toggleProduct(id: string, available: boolean): Promise<Product> {
  return apiFetch<Product>(`/api/las/products/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteProduct(id: string): Promise<void> {
  return apiFetch<void>(`/api/las/products/${id}`, { method: 'DELETE' })
}

// ---- Variantes de um produto (grade cor × lote de tingimento com estoque) ----

export type CreateVariantInput = {
  color: string
  dyeLot: string
  sku?: string | null
  priceCents?: number | null // null = herda o basePriceCents do produto
  stockQty: number
}

export type UpdateVariantInput = Partial<CreateVariantInput> & { available?: boolean }

export function listVariants(productId: string): Promise<{ items: Variant[] }> {
  return apiFetch<{ items: Variant[] }>(`/api/las/products/${productId}/variants`)
}

export function createVariant(productId: string, input: CreateVariantInput): Promise<Variant> {
  return apiFetch<Variant>(`/api/las/products/${productId}/variants`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateVariant(
  productId: string,
  variantId: string,
  input: UpdateVariantInput,
): Promise<Variant> {
  return apiFetch<Variant>(`/api/las/products/${productId}/variants/${variantId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function toggleVariant(productId: string, variantId: string, available: boolean): Promise<Variant> {
  return apiFetch<Variant>(`/api/las/products/${productId}/variants/${variantId}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ available }),
  })
}

export function deleteVariant(productId: string, variantId: string): Promise<void> {
  return apiFetch<void>(`/api/las/products/${productId}/variants/${variantId}`, { method: 'DELETE' })
}
