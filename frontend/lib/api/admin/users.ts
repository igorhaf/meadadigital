import { apiFetch } from '@/lib/api/client'

/** Item da lista global de usuários (camada 6.2). */
export type AdminUserListItem = {
  id: string
  email: string
  role: string
  companyName: string
  suspended: boolean
  lastLoginAt: string | null
  createdAt: string
}

export type AdminUserPage = {
  items: AdminUserListItem[]
  total: number
  page: number
  pageSize: number
}

/** Ação do super-admin sobre o usuário (histórico). */
export type AdminUserAction = {
  action: string
  payload: string | null
  createdAt: string
}

/** Detalhe de um usuário global. */
export type AdminUserDetail = {
  id: string
  email: string
  role: string
  companyId: string
  companyName: string
  suspended: boolean
  suspendedAt: string | null
  suspendedReason: string | null
  lastLoginAt: string | null
  createdAt: string
  recentActions: AdminUserAction[]
}

export type UserFilters = {
  q?: string
  companyId?: string
  role?: string
  suspended?: boolean
  page?: number
  pageSize?: number
}

function qs(filters: UserFilters): string {
  const p = new URLSearchParams()
  if (filters.q) p.set('q', filters.q)
  if (filters.companyId) p.set('companyId', filters.companyId)
  if (filters.role) p.set('role', filters.role)
  if (filters.suspended != null) p.set('suspended', String(filters.suspended))
  if (filters.page != null) p.set('page', String(filters.page))
  if (filters.pageSize != null) p.set('pageSize', String(filters.pageSize))
  const s = p.toString()
  return s ? `?${s}` : ''
}

export async function getUsers(filters: UserFilters = {}): Promise<AdminUserPage> {
  return apiFetch<AdminUserPage>(`/admin/users${qs(filters)}`)
}

export async function getUser(id: string): Promise<AdminUserDetail> {
  return apiFetch<AdminUserDetail>(`/admin/users/${id}`)
}

export async function suspendUser(id: string, reason?: string): Promise<void> {
  return apiFetch<void>(`/admin/users/${id}/suspend`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason ?? null }),
  })
}

export async function reactivateUser(id: string): Promise<void> {
  return apiFetch<void>(`/admin/users/${id}/reactivate`, { method: 'POST' })
}

/** Reset de senha pelo admin — hoje retorna 501 (SUPABASE_SERVICE_ROLE_KEY ausente). */
export async function resetUserPassword(id: string): Promise<void> {
  return apiFetch<void>(`/admin/users/${id}/password-reset`, { method: 'POST' })
}

export async function deleteUser(id: string): Promise<void> {
  return apiFetch<void>(`/admin/users/${id}`, { method: 'DELETE' })
}
