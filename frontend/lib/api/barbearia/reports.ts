import { apiFetch } from '@/lib/api/client'
import type { BarberReportSummary } from '@/profiles/barbearia/barber-types'

export function getReportSummary(months: number): Promise<BarberReportSummary> {
  return apiFetch<BarberReportSummary>(`/api/barbearia/reports/summary?months=${months}`)
}
