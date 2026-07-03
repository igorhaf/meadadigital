import { apiFetch } from '@/lib/api/client'
import type { Checkin } from '@/profiles/academia/academia-types'

export type CreateCheckinInput = {
  membershipId: string
  classId: string
  source?: string // 'ia' | 'painel'
  notes?: string | null
}

export function createCheckin(input: CreateCheckinInput): Promise<Checkin> {
  return apiFetch<Checkin>('/api/academia/checkins', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function listCheckins(
  opts: { classId?: string; from?: string; to?: string } = {},
): Promise<{ items: Checkin[] }> {
  const p = new URLSearchParams()
  if (opts.classId) p.set('classId', opts.classId)
  if (opts.from) p.set('from', opts.from)
  if (opts.to) p.set('to', opts.to)
  const qs = p.toString()
  return apiFetch<{ items: Checkin[] }>(`/api/academia/checkins${qs ? `?${qs}` : ''}`)
}
