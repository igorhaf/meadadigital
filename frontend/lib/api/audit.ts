import { apiFetch } from './client'

/**
 * Entrada do audit log (camada 5.20 #78) — shape do /admin/audit-logs. metadata é o jsonb
 * cru da ação (objeto livre; null se o backend não conseguiu parsear). userId/entityId
 * podem ser null (ações sem autor resolvido / sem entidade específica). createdAt ISO string.
 */
export type AuditLogEntry = {
  id: string
  userId: string | null
  action: string
  entity: string
  entityId: string | null
  metadata: Record<string, unknown> | null
  createdAt: string
}

/**
 * Lista as ações auditadas da empresa do admin, mais recentes primeiro. Filtros opcionais
 * entity/action (ignorados pelo backend se vazios) + limit (cap 200 no backend). Monta a
 * query string só com os filtros preenchidos.
 */
export async function getAuditLogs(filters?: {
  entity?: string
  action?: string
  limit?: number
}): Promise<AuditLogEntry[]> {
  const params = new URLSearchParams()
  if (filters?.entity?.trim()) {
    params.set('entity', filters.entity.trim())
  }
  if (filters?.action?.trim()) {
    params.set('action', filters.action.trim())
  }
  if (filters?.limit != null) {
    params.set('limit', String(filters.limit))
  }
  const qs = params.toString()
  return apiFetch<AuditLogEntry[]>(`/admin/audit-logs${qs ? `?${qs}` : ''}`)
}
