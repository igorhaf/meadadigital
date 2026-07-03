import { apiFetch } from '@/lib/api/client'

/** Convite na visão global cross-tenant (camada 6.2). status derivado no backend. */
export type AdminInvitation = {
  id: string
  email: string
  companyName: string
  status: 'pending' | 'accepted' | 'expired' | 'revoked'
  createdAt: string
  expiresAt: string
}

export type AdminInvitationPage = {
  items: AdminInvitation[]
  total: number
  page: number
  pageSize: number
}

export type InvitationFilters = {
  status?: string
  companyId?: string
  createdAfter?: string
  page?: number
  pageSize?: number
}

function qs(filters: InvitationFilters): string {
  const p = new URLSearchParams()
  if (filters.status) p.set('status', filters.status)
  if (filters.companyId) p.set('companyId', filters.companyId)
  if (filters.createdAfter) p.set('createdAfter', filters.createdAfter)
  if (filters.page != null) p.set('page', String(filters.page))
  if (filters.pageSize != null) p.set('pageSize', String(filters.pageSize))
  const s = p.toString()
  return s ? `?${s}` : ''
}

/** Lista TODOS os convites do SaaS (cross-tenant). Endpoint /all (distinto do tenant). */
export async function getAllInvitations(
  filters: InvitationFilters = {},
): Promise<AdminInvitationPage> {
  return apiFetch<AdminInvitationPage>(`/admin/invitations/all${qs(filters)}`)
}

export async function revokeInvitation(id: string): Promise<void> {
  return apiFetch<void>(`/admin/invitations/${id}/revoke`, { method: 'POST' })
}
