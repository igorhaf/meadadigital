import { getProfileBySubdomain, GENERIC_PROFILE, type Profile } from '@/lib/profiles/profile-type'

/**
 * Resolução de subdomínio (camada 7.0). Meada se apresenta como N produtos por subdomínio:
 * processo.meadadigital.local → ProcessoBot, etc. localhost e o domínio-base (sem subdomínio)
 * caem para 'meada' (genérico/universal): login universal, comportamento atual.
 *
 * <p>O middleware injeta o header 'x-meada-subdomain' no server; no client (login, apiFetch)
 * lemos direto de window.location.hostname com a mesma regra — uma fonte de verdade só.
 */

const SUBDOMAIN_HEADER = 'x-meada-subdomain'
export { SUBDOMAIN_HEADER }

/** Extrai o subdomínio de um hostname. localhost / domínio-base → 'meada' (genérico). */
export function subdomainFromHost(host: string | null | undefined): string {
  if (!host) return 'meada'
  // tira porta (localhost:3000 → localhost)
  const hostname = host.split(':')[0]
  if (hostname === 'localhost' || hostname === '127.0.0.1') return 'meada'

  const parts = hostname.split('.')
  // "meadadigital.local" (2 partes, sem subdomínio) → genérico.
  // "processo.meadadigital.local" (3+ partes) → primeiro segmento.
  if (parts.length <= 2) return 'meada'
  const first = parts[0]
  // se o primeiro segmento não é um perfil conhecido, trata como genérico (defensivo).
  return getProfileBySubdomain(first) ? first : 'meada'
}

/**
 * 1º segmento CRU do host (sem cair para 'meada' quando desconhecido). É o slug candidato a
 * EMPRESA em {empresa}.meadadigital.com — o subdomainFromHost engole desconhecidos pra 'meada',
 * o que perderia o slug. null quando não há subdomínio (localhost / domínio-base / 2 partes).
 */
export function rawSubdomain(host: string | null | undefined): string | null {
  if (!host) return null
  const hostname = host.split(':')[0]
  if (hostname === 'localhost' || hostname === '127.0.0.1') return null
  const parts = hostname.split('.')
  if (parts.length <= 2) return null
  return parts[0]
}

/** True se o segmento é um subdomínio de NICHO conhecido (perfil vertical). */
export function isNicheSubdomain(sub: string): boolean {
  return !!getProfileBySubdomain(sub)
}

/**
 * URL base do Meada para o host atual (preserva esquema/porta, troca o host pelo domínio-base).
 * Ex.: padaria.meadadigital.com → https://meadadigital.com ; sub.meadadigital.local:3000 →
 * http://meadadigital.local:3000.
 */
export function baseUrl(reqUrl: URL, host: string | null | undefined): URL {
  const hostname = (host ?? '').split(':')[0]
  const port = (host ?? '').split(':')[1]
  const parts = hostname.split('.')
  const base = parts.length <= 2 ? hostname : parts.slice(1).join('.')
  const url = new URL(reqUrl)
  url.host = port ? `${base}:${port}` : base
  url.pathname = '/'
  url.search = ''
  return url
}

/**
 * URL de login do NICHO para uma empresa sem CMS: {profileSubdomain}.<base>/login.
 * Ex.: empresa perfil 'comida' em padaria.meadadigital.com → comida.meadadigital.com/login.
 */
export function nicheLoginUrl(reqUrl: URL, host: string | null | undefined, profileSubdomain: string): URL {
  const base = baseUrl(reqUrl, host)
  const port = base.port
  base.host = port ? `${profileSubdomain}.${base.hostname}:${port}` : `${profileSubdomain}.${base.hostname}`
  base.pathname = '/login'
  return base
}

/** Subdomínio atual no browser (client-only). 'meada' em SSR/sem window. */
export function currentSubdomain(): string {
  if (typeof window === 'undefined') return 'meada'
  return subdomainFromHost(window.location.hostname)
}

/** Perfil resolvido do subdomínio atual (client). Cai para o genérico se desconhecido. */
export function currentProfile(): Profile {
  return getProfileBySubdomain(currentSubdomain()) ?? GENERIC_PROFILE
}

/** True quando o subdomínio é o universal/genérico (login universal, sem profile-match). */
export function isUniversalSubdomain(sub: string): boolean {
  return sub === 'meada'
}

/**
 * True quando o host é um domínio do PRÓPRIO Meada (painel/produtos) — localhost, 127.0.0.1, ou
 * qualquer coisa sob meadadigital.local / meadadigital.com. False = domínio CUSTOM de tenant
 * (CMS, SM-M): o middleware reescreve esses pro render público por domínio.
 */
export function isMeadaHost(host: string | null | undefined): boolean {
  if (!host) return true
  const hostname = host.split(':')[0]
  if (hostname === 'localhost' || hostname === '127.0.0.1') return true
  return hostname === 'meadadigital.local' || hostname.endsWith('.meadadigital.local')
    || hostname === 'meadadigital.com' || hostname.endsWith('.meadadigital.com')
}
