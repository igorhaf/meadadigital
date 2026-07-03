import { apiFetch } from '@/lib/api/client'
import type { CmsRow } from '@/lib/cms/cms-block-type'
import { normalizeToTree } from '@/lib/cms/cms-tree'

/**
 * SDK do CMS do TENANT (SM-N), multi-página. Atrás do gate de feature (403 feature_disabled se o
 * nicho não tem CMS). Site (config) + páginas. O render público NÃO usa este SDK — é server-side.
 */

/** preset: tema com identidade própria (ex.: 'meada-dark' = cara da marca Meada). Ausente = tema
 * genérico (primaryColor + dark). theme é JSONB livre no backend — adicionar campo não toca o Spring. */
export type CmsThemePreset = 'meada-dark'
/** themeId = `{nicho}-{archetype}` do catálogo de temas (lib/cms/themes). theme é JSONB livre no
 * backend — adicionar o campo não toca o Spring. */
export type CmsTheme = { primaryColor?: string; dark?: boolean; preset?: CmsThemePreset; themeId?: string }

export type CmsSite = {
  companyId: string
  slug: string
  domain: string | null
  domainVerified: boolean
  verifyToken: string | null
  published: boolean
  theme: CmsTheme
  createdAt: string
  updatedAt: string
}

export type CmsPage = {
  id: string
  companyId: string
  pageSlug: string
  title: string
  blocks: CmsRow[]   // árvore (linhas → colunas → blocos); normalizada na leitura
  isHome: boolean
  position: number
  published: boolean
  createdAt: string
  updatedAt: string
}

export type CmsSiteView = { site: CmsSite; pages: CmsPage[] }

export async function getCmsSite(): Promise<CmsSiteView> {
  const view = await apiFetch<CmsSiteView>('/api/cms/site')
  // normaliza cada página: flat legado → árvore (idempotente). O editor lê pages[].blocks.
  return { ...view, pages: view.pages.map((p) => ({ ...p, blocks: normalizeToTree(p.blocks) })) }
}

export function setCmsPublished(published: boolean): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/publish', { method: 'PUT', body: JSON.stringify({ published }) })
}

export function setCmsTheme(theme: CmsTheme): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/theme', { method: 'PUT', body: JSON.stringify({ theme }) })
}

export function setCmsDomain(domain: string | null): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/domain', { method: 'PUT', body: JSON.stringify({ domain }) })
}

export function startDomainVerification(): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/verify/start', { method: 'POST' })
}

export function verifyDomain(): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/verify', { method: 'POST' })
}

export function createCmsPage(pageSlug: string, title: string): Promise<CmsPage> {
  return apiFetch<CmsPage>('/api/cms/pages', { method: 'POST', body: JSON.stringify({ pageSlug, title }) })
}

export function saveCmsPage(
  id: string,
  input: { title?: string; blocks?: CmsRow[]; published?: boolean },
): Promise<CmsPage> {
  return apiFetch<CmsPage>(`/api/cms/pages/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function setCmsHome(id: string): Promise<CmsPage> {
  return apiFetch<CmsPage>(`/api/cms/pages/${id}/home`, { method: 'PUT' })
}

export function deleteCmsPage(id: string): Promise<void> {
  return apiFetch<void>(`/api/cms/pages/${id}`, { method: 'DELETE' })
}
