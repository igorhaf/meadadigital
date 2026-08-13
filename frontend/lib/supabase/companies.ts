import type { Company } from '@/lib/api/companies'

import { createClient } from './client'

/**
 * Leitura da empresa do tenant logado via Supabase SDK + RLS (NÃO via Spring).
 *
 * <p>Decisão macro 4 da camada 4: dados internos do tenant vêm pelo SDK do Supabase,
 * onde o RLS (companies_select_own: id = app.company_id()) garante que o tenant-admin
 * só enxerga a PRÓPRIA empresa. Super-admin não usa esta função (ele não tem linha em
 * public.users → app.company_id() = NULL → SELECT volta vazio; super-admin lê via
 * Spring/service_role em /admin/companies).
 *
 * <p>.single() exige exatamente uma linha: o RLS filtra para a empresa do tenant, então
 * o resultado é 0 ou 1 linha. Se 0 (tenant sem empresa — não deveria ocorrer com
 * provisão correta), .single() lança PostgrestError, tratado como erro pela query.
 *
 * <p>O SDK devolve as colunas cruas do banco (snake_case: created_at, palette_id).
 * Mapeamos para o shape Company (createdAt, paletteId camelCase) reusado do contrato
 * REST, para a UI tratar empresa de forma uniforme independente da fonte (SDK ou Spring).
 * palette_id é NOT NULL DEFAULT 'meada-default' no banco (camada 5.0) → nunca null.
 */
export async function getMyCompany(): Promise<Company> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('companies')
    .select('id, name, slug, status, created_at, palette_id, profile_id')
    .single()

  if (error) {
    throw error
  }

  return {
    id: data.id,
    name: data.name,
    slug: data.slug,
    status: data.status,
    createdAt: data.created_at,
    paletteId: data.palette_id,
    profileId: data.profile_id,
  }
}
