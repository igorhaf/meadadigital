import { apiFetch } from '@/lib/api/client'
import type { AcademiaMembershipStatusId } from '@/profiles/academia/academia-membership-status'
import type { Membership } from '@/profiles/academia/academia-types'

type MembershipPage = { items: Membership[]; total: number; page: number; pageSize: number }

export type CreateMembershipInput = {
  planId: string
  classIds: string[]
  studentName: string
  studentPhone?: string | null
  notes?: string | null
}

export function listMemberships(
  opts: { status?: string; planId?: string; classId?: string; page?: number; pageSize?: number } = {},
): Promise<MembershipPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.planId) p.set('planId', opts.planId)
  if (opts.classId) p.set('classId', opts.classId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<MembershipPage>(`/api/academia/memberships${qs ? `?${qs}` : ''}`)
}

export function getMembership(id: string): Promise<Membership> {
  return apiFetch<Membership>(`/api/academia/memberships/${id}`)
}

export function createMembership(input: CreateMembershipInput): Promise<Membership> {
  return apiFetch<Membership>('/api/academia/memberships', { method: 'POST', body: JSON.stringify(input) })
}

export function updateMembershipStatus(id: string, newStatus: AcademiaMembershipStatusId): Promise<Membership> {
  return apiFetch<Membership>(`/api/academia/memberships/${id}/status`, {
    method: 'PATCH', body: JSON.stringify({ newStatus }),
  })
}
