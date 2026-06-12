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
}

export async function getMe(): Promise<Me> {
  return apiFetch<Me>('/admin/me')
}
