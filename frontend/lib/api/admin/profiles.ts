import { apiFetch } from '@/lib/api/client'

/** Um perfil no catálogo (camada 7.0). Espelha ProfileType.java / profile-type.ts. */
export type ProfileCatalogItem = {
  id: string
  productName: string
  subdomain: string
  defaultPaletteId: string
}

/** Resposta de GET /admin/me/profile-match. match=false vem com expected* no mismatch. */
export type ProfileMatch = {
  match: boolean
  productName?: string
  expectedSubdomain?: string
  expectedProductName?: string
}

/** Catálogo de perfis (super-admin). Alimenta o dropdown de perfil no painel root. */
export function getProfiles(): Promise<{ items: ProfileCatalogItem[] }> {
  return apiFetch<{ items: ProfileCatalogItem[] }>('/admin/profiles')
}

/** Valida se o usuário logado pode acessar o subdomínio dado (camada 7.0). */
export function getProfileMatch(subdomain: string): Promise<ProfileMatch> {
  return apiFetch<ProfileMatch>(`/admin/me/profile-match?subdomain=${encodeURIComponent(subdomain)}`)
}
