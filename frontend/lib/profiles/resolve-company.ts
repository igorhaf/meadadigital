/**
 * Resolução de {empresa}.meadadigital.com para o middleware (roteamento de domínios).
 * Consulta o backend público /public/companies/resolve/{slug} e cacheia em memória (TTL 60s)
 * para não bater no backend a cada navegação — a resolução de empresa é estável.
 *
 * O middleware roda no edge/server; usa a base INTERNA do backend (mesmo padrão do CMS SSR).
 */

export type CompanyResolve = {
  exists: boolean
  profileId: string | null
  profileSubdomain: string | null
  hasCms: boolean
}

const NOT_FOUND: CompanyResolve = {
  exists: false,
  profileId: null,
  profileSubdomain: null,
  hasCms: false,
}

const TTL_MS = 60_000
const cache = new Map<string, { value: CompanyResolve; expires: number }>()

function backendBase(): string {
  return process.env.CMS_BACKEND_URL || process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8095'
}

export async function resolveCompany(
  slug: string,
  now: number = Date.now(),
): Promise<CompanyResolve> {
  const hit = cache.get(slug)
  if (hit && hit.expires > now) return hit.value
  try {
    const res = await fetch(
      `${backendBase()}/public/companies/resolve/${encodeURIComponent(slug)}`,
      {
        cache: 'no-store',
      },
    )
    if (!res.ok) return NOT_FOUND // falha → trata como inexistente (defensivo)
    const value = (await res.json()) as CompanyResolve
    cache.set(slug, { value, expires: now + TTL_MS })
    return value
  } catch {
    return NOT_FOUND
  }
}
