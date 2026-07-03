import { apiFetch } from '@/lib/api/client'

export type AnnouncementSeverity = 'info' | 'warning' | 'critical'

/** Anúncio na visão do super-admin (camada 6.7). */
export type Announcement = {
  id: string
  title: string
  body: string
  severity: AnnouncementSeverity
  publishedAt: string
  expiresAt: string | null
  dismissable: boolean
  createdBy: string | null
}

type Page<T> = { items: T[]; total: number; page: number; pageSize: number }

export type CreateAnnouncementInput = {
  title: string
  body: string
  severity: AnnouncementSeverity
  expiresAt?: string | null
  dismissable?: boolean
}

export type UpdateAnnouncementInput = Partial<CreateAnnouncementInput>

export function listAnnouncements(
  filters: { status?: 'active' | 'expired'; page?: number; pageSize?: number } = {},
): Promise<Page<Announcement>> {
  const p = new URLSearchParams()
  if (filters.status) p.set('status', filters.status)
  if (filters.page !== undefined) p.set('page', String(filters.page))
  if (filters.pageSize !== undefined) p.set('pageSize', String(filters.pageSize))
  const qs = p.toString()
  return apiFetch<Page<Announcement>>(`/admin/announcements${qs ? `?${qs}` : ''}`)
}

export function createAnnouncement(input: CreateAnnouncementInput): Promise<Announcement> {
  return apiFetch<Announcement>('/admin/announcements', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateAnnouncement(
  id: string,
  input: UpdateAnnouncementInput,
): Promise<Announcement> {
  return apiFetch<Announcement>(`/admin/announcements/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** Soft delete (expira o anúncio agora). */
export function expireAnnouncement(id: string): Promise<void> {
  return apiFetch<void>(`/admin/announcements/${id}`, { method: 'DELETE' })
}
