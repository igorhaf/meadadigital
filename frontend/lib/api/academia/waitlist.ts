import { apiFetch } from '@/lib/api/client'
import type { WaitlistEntry, WaitlistStatusId } from '@/profiles/academia/academia-types'

export type EnqueueWaitlistInput = {
  classId: string
  contactId?: string | null
  studentName: string
  studentPhone?: string | null
}

export function listWaitlist(
  classId: string,
  opts: { onlyWaiting?: boolean } = {},
): Promise<{ items: WaitlistEntry[] }> {
  const p = new URLSearchParams()
  p.set('classId', classId)
  if (opts.onlyWaiting !== undefined) p.set('onlyWaiting', String(opts.onlyWaiting))
  return apiFetch<{ items: WaitlistEntry[] }>(`/api/academia/waitlist?${p.toString()}`)
}

export function enqueueWaitlist(input: EnqueueWaitlistInput): Promise<WaitlistEntry> {
  return apiFetch<WaitlistEntry>('/api/academia/waitlist', { method: 'POST', body: JSON.stringify(input) })
}

export function updateWaitlistStatus(id: string, status: WaitlistStatusId): Promise<WaitlistEntry> {
  return apiFetch<WaitlistEntry>(`/api/academia/waitlist/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ status }),
  })
}
