import { apiFetch } from './client'

/** As 3 ações de acesso registráveis (espelha o enum do backend/schema). */
export type AccessAction = 'login_success' | 'login_failed' | 'password_changed'

/**
 * Entrada de log de acesso (camada 5.24 #92) — shape do /admin/access-logs. ip/userAgent
 * podem vir null (cliente sem header). createdAt ISO string.
 */
export type AccessLogEntry = {
  id: string
  email: string | null
  action: AccessAction
  ip: string | null
  userAgent: string | null
  createdAt: string
}

/**
 * Registra um evento de autenticação (POST /api/access-logs) — PÚBLICO, sem auth. fetch
 * direto (não apiFetch, que injetaria Bearer e trataria 401 com signOut): um login_failed
 * não tem sessão. Best-effort: o caller (login) envolve em try/catch e nunca bloqueia o
 * fluxo se isto falhar.
 */
export async function recordAccessLog(action: AccessAction, email: string): Promise<void> {
  const base = process.env.NEXT_PUBLIC_API_URL
  if (!base) {
    throw new Error('NEXT_PUBLIC_API_URL não configurada.')
  }
  await fetch(`${base}/api/access-logs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, email }),
  })
}

/** Lista os logs de acesso da empresa do admin, mais recentes primeiro (cap 100 no backend). */
export async function getAccessLogs(): Promise<AccessLogEntry[]> {
  return apiFetch<AccessLogEntry[]>('/admin/access-logs')
}
