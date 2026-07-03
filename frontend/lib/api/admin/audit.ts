import { apiFetch } from '@/lib/api/client'

type Page<T> = { items: T[]; total: number; page: number; pageSize: number }

/** Linha do audit_log global (camada 6.5). metadata é o jsonb cru (objeto). */
export type GlobalAuditLog = {
  id: string
  companyName: string | null
  userId: string | null
  action: string
  entity: string
  entityId: string | null
  metadata: unknown
  createdAt: string
}

/** Linha do access_logs global. */
export type GlobalAccessLog = {
  id: string
  companyName: string | null
  email: string | null
  action: string
  ip: string | null
  userAgent: string | null
  createdAt: string
}

/** Linha do admin_action_log (rastro do super-admin). */
export type AdminActionRow = {
  id: string
  superAdminUserId: string
  action: string
  targetType: string
  targetId: string | null
  payload: unknown
  createdAt: string
}

function qs(params: Record<string, string | number | undefined>): string {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') p.set(k, String(v))
  }
  const s = p.toString()
  return s ? `?${s}` : ''
}

export function getGlobalAuditLogs(
  filters: {
    companyId?: string
    action?: string
    entity?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<Page<GlobalAuditLog>> {
  return apiFetch<Page<GlobalAuditLog>>(`/admin/audit/all${qs(filters)}`)
}

export function getGlobalAccessLogs(
  filters: {
    action?: string
    ip?: string
    userAgent?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<Page<GlobalAccessLog>> {
  return apiFetch<Page<GlobalAccessLog>>(`/admin/security/access-logs/all${qs(filters)}`)
}

export function getAdminActions(
  filters: {
    action?: string
    targetType?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<Page<AdminActionRow>> {
  return apiFetch<Page<AdminActionRow>>(`/admin/actions${qs(filters)}`)
}
