import { apiFetch } from '@/lib/api/client'

export type MyAnnouncementSeverity = 'info' | 'warning' | 'critical'

/** Anúncio ativo na visão do usuário logado (banner do AppShell — camada 6.7). */
export type MyAnnouncement = {
  id: string
  title: string
  body: string
  severity: MyAnnouncementSeverity
  publishedAt: string
  expiresAt: string | null
  dismissable: boolean
}

/** Anúncios publicados, não-expirados e não-dispensados pelo usuário atual. */
export function getMyAnnouncements(): Promise<{ items: MyAnnouncement[] }> {
  return apiFetch<{ items: MyAnnouncement[] }>('/admin/me/announcements')
}

/** Dispensa um anúncio para o usuário atual (idempotente). */
export function dismissAnnouncement(id: string): Promise<void> {
  return apiFetch<void>(`/admin/me/announcements/${id}/dismiss`, { method: 'POST' })
}
