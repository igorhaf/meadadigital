import { apiFetch } from '@/lib/api/client'

/**
 * SDK da VITRINE de nichos do super-admin. O root marca quais nichos são destaque (aparecem na
 * home, até maxFeatured) e define a ordem (grid + página /produtos). A grade é computada no
 * backend (todos os nichos showcasáveis + featured/ordem do banco). Autorização: backend (403
 * forbidden_not_super_admin para não-root).
 */

/** Uma linha da grade da vitrine. */
export type ShowcaseRow = {
  profileId: string
  productName: string
  subdomain: string
  paletteId: string
  featured: boolean
  displayOrder: number
}

export type ShowcaseGrid = { niches: ShowcaseRow[]; maxFeatured: number }

export function getNicheShowcase(): Promise<ShowcaseGrid> {
  return apiFetch<ShowcaseGrid>('/admin/niches/showcase')
}

export function setNicheShowcase(
  profileId: string,
  featured: boolean,
  displayOrder: number,
): Promise<unknown> {
  return apiFetch<unknown>(`/admin/niches/showcase/${profileId}`, {
    method: 'PUT',
    body: JSON.stringify({ featured, displayOrder }),
  })
}
