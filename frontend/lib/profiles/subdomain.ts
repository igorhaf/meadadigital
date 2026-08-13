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
