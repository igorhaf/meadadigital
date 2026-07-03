import { apiFetch } from '@/lib/api/client'
import type { ComidaReportSummary } from '@/profiles/comida/comida-types'

export function getReportSummary(months: number): Promise<ComidaReportSummary> {
  return apiFetch<ComidaReportSummary>(`/api/comida/reports/summary?months=${months}`)
}
