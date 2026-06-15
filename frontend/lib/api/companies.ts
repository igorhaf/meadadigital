import { apiFetch } from './client'

/**
 * Empresa na listagem do painel super-admin (GET /admin/companies). Espelha o
 * CompanyResponse do backend.
 *
 * id e createdAt são string (JSON serializa UUID e Instant como string). status é union
 * literal — mesma razão do backend (CHECK constraint garante; frontend só tipa).
 * createdAt fica como string ISO-8601 cru: a formatação (new Date(...)) é da TELA, não
 * desta camada (separation of concerns — aqui é só shape).
 *
 * paletteId é OBRIGATÓRIO (camada 5.1.a): o backend passou a sempre retorná-lo no
 * CompanyResponse (NOT NULL DEFAULT 'meada-default' no banco), e o SDK getMyCompany
 * também o traz. Tipo estrito reflete a realidade — toda Company tem paletteId, seja da
 * fonte REST (super-admin) ou SDK (tenant). O uso visual (swatch na listagem) vem na
 * 5.1.b; aqui o tipo só fica honesto.
 */
export type Company = {
  id: string
  name: string
  slug: string
  status: 'active' | 'suspended'
  createdAt: string
  paletteId: string
}

/**
 * Lista GLOBAL de empresas. Super-admin only — mas a autorização é do backend (retorna
 * 403 forbidden_not_super_admin para tenant-admin); o frontend só consome e trata o erro.
 */
export async function getCompanies(): Promise<Company[]> {
  return apiFetch<Company[]>('/admin/companies')
}

/**
 * Cria uma empresa (super-admin only — autorização no backend, 403 para tenant-admin).
 * O 201 devolve o CompanyResponse criado (mesmo shape de Company). paletteId é a paleta
 * escolhida no modal (camada 5.1.a). Erros relevantes ao form: 409 slug_already_exists
 * (slug em uso) e 400 validation_error (defensivo; o zod client-side é a 1ª barreira) —
 * ambos chegam como ApiError(status, reason) via apiFetch.
 */
export async function createCompany(
  payload: { name: string; slug: string; paletteId: string },
): Promise<Company> {
  return apiFetch<Company>('/admin/companies', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
