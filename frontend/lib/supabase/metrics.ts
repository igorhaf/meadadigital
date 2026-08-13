import { createClient } from './client'

/** Um ponto da série diária de mensagens (gráfico). */
export type MessagesByDay = {
  day: string // 'YYYY-MM-DD'
  inbound: number
  outbound: number
}

/** FAQ ativa listada no painel (ordem cronológica; ranking por uso é fase futura). */
export type TopFaq = {
  id: string
  question: string
  createdAt: string
}

/**
 * Métricas do dashboard do tenant (camada 5.12), todas dos últimos 30 dias. Vêm de uma
 * RPC única (get_tenant_metrics) que monta o jsonb no banco — 1 round-trip.
 */
export type TenantMetrics = {
  messagesInbound30d: number
  messagesOutbound30d: number
  conversationsStarted30d: number
  contactsNew30d: number
  messagesByDay: MessagesByDay[]
  topFaqs: TopFaq[]
  avgResponseSeconds: number | null
}

/**
 * Busca todas as métricas do tenant via RPC public.get_tenant_metrics (SECURITY INVOKER,
 * respeita o RLS — só métricas da própria empresa). Retorna o jsonb já tipado.
 */
export async function getTenantMetrics(): Promise<TenantMetrics> {
  const supabase = createClient()
  const { data, error } = await supabase.rpc('get_tenant_metrics')
  if (error) {
    throw error
  }
  return data as TenantMetrics
}
