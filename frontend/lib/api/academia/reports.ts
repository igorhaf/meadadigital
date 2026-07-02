import { apiFetch } from '@/lib/api/client'
import type { OccupancyRow, SummaryReport } from '@/profiles/academia/academia-types'

export function getSummaryReport(): Promise<SummaryReport> {
  return apiFetch<SummaryReport>('/api/academia/reports/summary')
}

export function getOccupancyReport(): Promise<{ items: OccupancyRow[] }> {
  return apiFetch<{ items: OccupancyRow[] }>('/api/academia/reports/occupancy')
}
