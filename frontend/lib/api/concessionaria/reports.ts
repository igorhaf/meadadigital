import { apiFetch } from '@/lib/api/client'
import type { ConcessionariaReportSummary } from '@/profiles/concessionaria/concessionaria-types'

export function getReportSummary(months: number): Promise<ConcessionariaReportSummary> {
  return apiFetch<ConcessionariaReportSummary>(
    `/api/concessionaria/reports/summary?months=${months}`,
  )
}
