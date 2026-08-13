import { apiFetch } from '@/lib/api/client'

/** Alerta de plataforma exibido no hub do super-admin (camada 6.0). */
export type AdminAlert = {
  severity: 'warning' | 'error'
  message: string
  link: string | null
}

/** Overview do hub do super-admin — espelha AdminOverviewResponse do backend. */
export type AdminOverview = {
  activeCompanies: number
  companiesCreatedThisMonth: number
  messagesToday: number
  messagesYesterday: number
  openConversations: number
  openConversationsCompanyCount: number
  geminiTokensThisMonth: number
  alerts: AdminAlert[]
}

/** GET /admin/dashboard/overview — KPIs agregados da plataforma (super-admin only). */
export async function getAdminOverview(): Promise<AdminOverview> {
  return apiFetch<AdminOverview>('/admin/dashboard/overview')
}
