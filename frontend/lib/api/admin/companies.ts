import { apiFetch } from '@/lib/api/client'

/**
 * SDK do drill-down de empresas do super-admin (camada 6.1). Tudo via apiFetch (injeta o
 * Bearer da sessão Supabase, trata 401/403). A autorização é do backend — um tenant-admin
 * recebe 403 forbidden_not_super_admin, tratado inline pela tela.
 */

/** Item da listagem (espelha CompanyResponse do backend). */
export type Company = {
  id: string
  name: string
  slug: string
  status: 'active' | 'suspended'
  createdAt: string
  paletteId: string
  profileId: string
}

/** Página de empresas (espelha CompanyPage do backend). */
export type CompanyPage = {
  items: Company[]
  total: number
  page: number
  pageSize: number
}

/** Detalhe de uma empresa (espelha CompanyDetailDto do backend). */
export type CompanyDetail = {
  id: string
  name: string
  slug: string
  status: 'active' | 'suspended'
  paletteId: string
  createdAt: string
  maxAdmins: number | null
  maxFaqs: number | null
  maxConversationsMonth: number | null
  usersCount: number
  contactsCount: number
  openConversations: number
  messagesLast30d: number
  lastActivityAt: string | null
  ownerEmail: string | null
  ownerName: string | null
  profileId: string
}

/** Nota interna por empresa (espelha AdminNoteDto). superAdminUserId não é resolvível
 *  para nome (super-admin não tem linha em public.users) — a tela exibe "Admin". */
export type AdminNote = {
  id: string
  superAdminUserId: string
  content: string
  createdAt: string
  updatedAt: string
}

/** Filtros da listagem. Campos vazios são omitidos da query. */
export type CompanyFilters = {
  status?: 'active' | 'suspended'
  q?: string
  createdAfter?: string
  page?: number
  pageSize?: number
}

/** Payload de edição (espelha UpdateCompanyRequest). Limites null = sem limite. */
export type UpdateCompanyPayload = {
  name: string
  slug: string
  paletteId: string
  maxAdmins: number | null
  maxFaqs: number | null
  maxConversationsMonth: number | null
  // Perfil vertical (camada 7.0). Opcional: omitido = não altera. Validado contra o catálogo.
  profileId?: string
}

function buildQuery(filters: CompanyFilters): string {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.q && filters.q.trim()) params.set('q', filters.q.trim())
  if (filters.createdAfter) params.set('created_after', filters.createdAfter)
  if (filters.page != null) params.set('page', String(filters.page))
  if (filters.pageSize != null) params.set('pageSize', String(filters.pageSize))
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

/** GET /admin/companies — lista paginada com filtros. */
export async function getCompanies(filters: CompanyFilters = {}): Promise<CompanyPage> {
  return apiFetch<CompanyPage>(`/admin/companies${buildQuery(filters)}`)
}

/** GET /admin/companies/{id} — detalhe com contadores. */
export async function getCompany(id: string): Promise<CompanyDetail> {
  return apiFetch<CompanyDetail>(`/admin/companies/${id}`)
}

/** PATCH /admin/companies/{id} — edita. 409 slug_already_exists chega como ApiError. */
export async function updateCompany(
  id: string,
  data: UpdateCompanyPayload,
): Promise<CompanyDetail> {
  return apiFetch<CompanyDetail>(`/admin/companies/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

/** POST /admin/companies/{id}/suspend — 409 already_suspended chega como ApiError. */
export async function suspendCompany(id: string, reason?: string): Promise<void> {
  return apiFetch<void>(`/admin/companies/${id}/suspend`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason ?? null }),
  })
}

/** POST /admin/companies/{id}/reactivate — 409 already_active chega como ApiError. */
export async function reactivateCompany(id: string): Promise<void> {
  return apiFetch<void>(`/admin/companies/${id}/reactivate`, { method: 'POST' })
}

/** DELETE /admin/companies/{id} — hard delete irreversível. */
export async function deleteCompany(id: string): Promise<void> {
  return apiFetch<void>(`/admin/companies/${id}`, { method: 'DELETE' })
}

/** Resposta do "entrar como empresa": token de uso único + email do admin alvo. */
export type ImpersonateResult = { tokenHash: string; email: string }

/**
 * POST /admin/companies/{id}/impersonate — super-admin entra no admin da empresa.
 * Devolve o tokenHash de um magic link; o chamador abre /auth/confirm?token_hash=…
 * numa nova aba pra estabelecer a sessão do tenant.
 */
export async function impersonateCompany(id: string): Promise<ImpersonateResult> {
  return apiFetch<ImpersonateResult>(`/admin/companies/${id}/impersonate`, { method: 'POST' })
}

/** GET /admin/companies/{id}/notes — notas internas, mais recentes primeiro. */
export async function listNotes(id: string): Promise<AdminNote[]> {
  return apiFetch<AdminNote[]>(`/admin/companies/${id}/notes`)
}

/** POST /admin/companies/{id}/notes — cria nota. */
export async function createNote(id: string, content: string): Promise<AdminNote> {
  return apiFetch<AdminNote>(`/admin/companies/${id}/notes`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  })
}

/** PATCH /admin/companies/{id}/notes/{noteId} — edita nota. */
export async function updateNote(
  id: string,
  noteId: string,
  content: string,
): Promise<AdminNote> {
  return apiFetch<AdminNote>(`/admin/companies/${id}/notes/${noteId}`, {
    method: 'PATCH',
    body: JSON.stringify({ content }),
  })
}

/** DELETE /admin/companies/{id}/notes/{noteId} — apaga nota. */
export async function deleteNote(id: string, noteId: string): Promise<void> {
  return apiFetch<void>(`/admin/companies/${id}/notes/${noteId}`, { method: 'DELETE' })
}
