import { createClient } from '@/lib/supabase/client'

import { apiFetch } from './client'

/** Um contato no ranking dos mais ativos (#68). messageCount = total de mensagens da empresa. */
export type TopContact = {
  contactId: string
  name: string | null
  phoneNumber: string
  messageCount: number
}

/**
 * Top 10 contatos mais ativos da empresa (camada 5.23 #68), do backend (GET /admin/contacts/top
 * — tenant-admin only). A agregação count-group-by fica no Spring/JdbcTemplate, mais correta
 * que via PostgREST.
 */
export async function getTopContacts(): Promise<TopContact[]> {
  return apiFetch<TopContact[]>('/admin/contacts/top')
}

/** As 4 contagens de um mês (atual ou anterior) na comparação mês a mês (#66). */
export type MonthlyCounts = {
  conversations: number
  messagesInbound: number
  messagesOutbound: number
  activeContacts: number
}

/**
 * Comparação mês a mês (camada 5.23 #66): contagens do mês calendário atual vs anterior e os
 * deltas (atual - anterior) por métrica. Do backend GET /admin/metrics/comparison (tenant-admin
 * only) — endpoint próprio, não mexe na RPC do dashboard.
 */
export type MetricsComparison = {
  current: MonthlyCounts
  previous: MonthlyCounts
  deltas: MonthlyCounts
}

export async function getMetricsComparison(): Promise<MetricsComparison> {
  return apiFetch<MetricsComparison>('/admin/metrics/comparison')
}

/**
 * Baixa o PDF de métricas (camada 5.23 #65). apiFetch só lida com JSON, então fazemos um fetch
 * direto: injeta o Bearer da sessão Supabase (mesmo token que o apiFetch usa), lê o corpo como
 * blob e dispara o download via createObjectURL + clique num <a> temporário. Lança em erro HTTP.
 */
export async function downloadMetricsPdf(): Promise<void> {
  const apiBase = process.env.NEXT_PUBLIC_API_URL
  if (!apiBase) {
    throw new Error('NEXT_PUBLIC_API_URL não configurada (ver .env.local / .env.example).')
  }

  const supabase = createClient()
  const { data } = await supabase.auth.getSession()
  const token = data?.session?.access_token

  const response = await fetch(`${apiBase}/admin/metrics/export.pdf`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error(`falha ao exportar PDF (HTTP ${response.status})`)
  }

  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'metricas.pdf'
  document.body.appendChild(a)
  a.click()
  a.remove()
  // libera o objeto URL após o clique (já agendado).
  URL.revokeObjectURL(url)
}
