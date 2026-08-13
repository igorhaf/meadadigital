import { apiFetch } from '@/lib/api/client'

/** KPIs grandes da plataforma (camada 6.3). Tokens são somas reais (6.2.5). */
export type GlobalKpis = {
  totalCompanies: number
  activeCompanies: number
  totalMessages30d: number
  totalConversations30d: number
  geminiTokensThisMonth: number
  geminiTokensLast30d: number
}

/** Comparação mês calendário atual vs anterior (mensagens e empresas criadas). */
export type GlobalComparison = {
  messagesThisMonth: number
  messagesLastMonth: number
  messagesDeltaPct: number
  companiesThisMonth: number
  companiesLastMonth: number
  companiesDeltaPct: number
}

export type TopTenant = { id: string; name: string; messagesLast30d: number }
export type AtRiskTenant = { id: string; name: string; lastActivityAt: string | null }
export type MonthlyGrowth = { month: string; count: number }

export type GlobalMetrics = {
  kpis: GlobalKpis
  comparison: GlobalComparison
  topTenants: TopTenant[]
  atRisk: AtRiskTenant[]
  companiesCreatedPerMonth: MonthlyGrowth[]
}

export function getGlobalMetrics(): Promise<GlobalMetrics> {
  return apiFetch<GlobalMetrics>('/admin/metrics/global')
}
