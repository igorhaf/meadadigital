import { apiFetch } from './client'

/**
 * Identidade do usuário logado (GET /admin/me). Fonte de verdade do papel para o
 * frontend decidir a UI (super-admin vê gestão; tenant-admin vê área restrita).
 *
 * companyId é {@code string | null} (não undefined) — espelha o shape do backend
 * ({@code companyId: uuid | null}): null EXPLÍCITO para super-admin.
 *
 * paletteId é SEMPRE string não-null (camada 5.0): "meada-default" para super-admin
 * (constante do backend), users.palette_id para tenant-admin. Consumido pelo
 * ThemeProvider para injetar as CSS vars do tema.
 */
export type Me = {
  email: string
  role: 'super_admin' | 'tenant_admin'
  companyId: string | null
  paletteId: string
  // role do usuário DENTRO do tenant (owner|admin|agent — camada 5.17 #75); null para
  // super-admin. Usado para guards de capacidade no frontend (ex.: só owner deleta empresa).
  tenantRole: 'owner' | 'admin' | 'agent' | null
  // Perfil vertical do tenant (camada 7.0): companies.profile_id. null para super-admin.
  profileId: string | null
  // Label do "produto" do perfil ("Meada" | "ProcessoBot" | …). Exibido no topo do sidebar.
  productName: string
  // Feature flags resolvidas do nicho (camada 9.0): { key → enabled }. Ausência = false (default
  // OFF). Vazio para super-admin. O root liga/desliga em /dashboard/profile-features.
  features: Record<string, boolean>
}

export async function getMe(): Promise<Me> {
  return apiFetch<Me>('/admin/me')
}

/** Helper: a feature `key` está ligada para o nicho deste usuário? Ausência = false. */
export function hasFeature(me: Me | undefined | null, key: string): boolean {
  return me?.features?.[key] === true
}
