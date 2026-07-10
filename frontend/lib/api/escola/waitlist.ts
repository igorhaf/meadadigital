import { apiFetch } from '@/lib/api/client'
import type { WaitlistEntry } from '@/profiles/escola/escola-types'

export function listWaitlist(classId: string): Promise<{ items: WaitlistEntry[] }> {
  return apiFetch<{ items: WaitlistEntry[] }>(`/api/escola/waitlist?classId=${classId}`)
}

export function notifyOpening(id: string): Promise<{ notified: boolean }> {
  return apiFetch<{ notified: boolean }>(`/api/escola/waitlist/${id}/notify`, { method: 'POST' })
}

export function updateWaitlistStatus(
  id: string,
  status: 'convertida' | 'desistiu',
): Promise<{ updated: boolean }> {
  return apiFetch<{ updated: boolean }>(`/api/escola/waitlist/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}
