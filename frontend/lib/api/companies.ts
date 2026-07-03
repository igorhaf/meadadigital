import type { Company } from './admin/companies'
import { apiFetch } from './client'

/**
 * Criação de empresa (POST /admin/companies). A listagem, detalhe e demais operações do
 * drill-down vivem em {@code lib/api/admin/companies.ts} (camada 6.1) — este módulo
 * mantém só o create, consumido pelo CreateCompanyDialog, e reexporta o tipo Company por
 * compatibilidade com os imports existentes.
 */
export type { Company }

/**
 * Cria uma empresa (super-admin only — autorização no backend, 403 para tenant-admin).
 * O 201 devolve o CompanyResponse criado (mesmo shape de Company). Erros relevantes ao
 * form: 409 slug_already_exists e 400 validation_error, ambos como ApiError(status, reason).
 */
export async function createCompany(payload: {
  name: string
  slug: string
  paletteId: string
}): Promise<Company> {
  return apiFetch<Company>('/admin/companies', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
