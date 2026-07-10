import type { CmsRow } from './cms-block-type'
import { normalizeToTree } from './cms-tree'

/**
 * Fetch SERVER-SIDE da página pública do CMS (SM-N). Roda no servidor Next; usa a base INTERNA do
 * backend (CMS_BACKEND_URL → http://backend:8095 no compose; cai p/ NEXT_PUBLIC_API_URL e localhost).
 * Sem auth — /public/cms/**. A view inclui tema + nav (páginas publicadas) pro render montar o menu.
 */

export type CmsNavItem = { pageSlug: string; title: string; isHome: boolean }
export type CmsThemePreset = 'meada-dark'
/** themeId = `{nicho}-{archetype}` do catálogo (lib/cms/themes). Quando presente, tem precedência
 * sobre primaryColor/dark — resolve paleta+forma+layout completos do tema escolhido. */
export type CmsTheme = {
  primaryColor?: string
  dark?: boolean
  preset?: CmsThemePreset
  themeId?: string
}
/** blocks agora é a ÁRVORE (CmsRow[]); normalizeToTree converte o flat legado na leitura. */
export type PublicCmsView = {
  title: string
  blocks: CmsRow[]
  theme: CmsTheme | null
  nav: CmsNavItem[]
}

function backendBase(): string {
  return process.env.CMS_BACKEND_URL || process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8095'
}

async function fetchPublic(path: string): Promise<PublicCmsView | null> {
  try {
    const res = await fetch(`${backendBase()}${path}`, { cache: 'no-store' })
    if (!res.ok) return null
    const view = (await res.json()) as {
      title: string
      blocks: unknown
      theme: CmsTheme | null
      nav: CmsNavItem[]
    }
    return { ...view, blocks: normalizeToTree(view.blocks) }
  } catch {
    return null
  }
}

export function fetchHomeBySlug(slug: string): Promise<PublicCmsView | null> {
  return fetchPublic(`/public/cms/by-slug/${encodeURIComponent(slug)}`)
}

export function fetchPageBySlug(slug: string, pageSlug: string): Promise<PublicCmsView | null> {
  return fetchPublic(
    `/public/cms/by-slug/${encodeURIComponent(slug)}/${encodeURIComponent(pageSlug)}`,
  )
}

export function fetchHomeByDomain(host: string): Promise<PublicCmsView | null> {
  return fetchPublic(`/public/cms/by-domain?host=${encodeURIComponent(host)}`)
}

export function fetchPageByDomain(host: string, pageSlug: string): Promise<PublicCmsView | null> {
  return fetchPublic(
    `/public/cms/by-domain/${encodeURIComponent(pageSlug)}?host=${encodeURIComponent(host)}`,
  )
}
