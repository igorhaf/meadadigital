import { apiFetch } from '@/lib/api/client'
import type { ConcessionariaWishlist } from '@/profiles/concessionaria/concessionaria-types'

export type CreateWishlistInput = {
  contactId: string
  brand?: string | null
  model?: string | null
  maxPriceCents?: number | null
  minYear?: number | null
  notes?: string | null
}

export function listWishlists(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: ConcessionariaWishlist[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: ConcessionariaWishlist[] }>(`/api/concessionaria/wishlists${qs}`)
}

export function createWishlist(input: CreateWishlistInput): Promise<ConcessionariaWishlist> {
  return apiFetch<ConcessionariaWishlist>('/api/concessionaria/wishlists', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function setWishlistActive(id: string, active: boolean): Promise<ConcessionariaWishlist> {
  return apiFetch<ConcessionariaWishlist>(`/api/concessionaria/wishlists/${id}/active`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deleteWishlist(id: string): Promise<void> {
  return apiFetch<void>(`/api/concessionaria/wishlists/${id}`, { method: 'DELETE' })
}
