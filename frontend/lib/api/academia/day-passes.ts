import { apiFetch } from '@/lib/api/client'
import type { DayPass } from '@/profiles/academia/academia-types'

export type CreateDayPassInput = {
  contactId?: string | null
  guestName: string
  guestPhone?: string | null
  classId?: string | null
  passDate?: string | null // "YYYY-MM-DD", default hoje no backend
  priceCents: number
}

export function listDayPasses(): Promise<{ items: DayPass[] }> {
  return apiFetch<{ items: DayPass[] }>('/api/academia/day-passes')
}

export function createDayPass(input: CreateDayPassInput): Promise<DayPass> {
  return apiFetch<DayPass>('/api/academia/day-passes', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function payDayPass(id: string): Promise<DayPass> {
  return apiFetch<DayPass>(`/api/academia/day-passes/${id}/pay`, { method: 'PATCH' })
}
