'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'

import {
  dismissAnnouncement,
  getMyAnnouncements,
  type MyAnnouncement,
  type MyAnnouncementSeverity,
} from '@/lib/api/announcements'

/** Classes de cor por severidade (banner do topo). */
const SEVERITY_STYLES: Record<MyAnnouncementSeverity, string> = {
  info: 'bg-muted text-foreground border-border',
  warning:
    'bg-amber-50 text-amber-900 border-amber-200 dark:bg-amber-950/40 dark:text-amber-200 dark:border-amber-900',
  critical:
    'bg-red-50 text-red-900 border-red-200 dark:bg-red-950/40 dark:text-red-200 dark:border-red-900',
}

/**
 * Banner de anúncios (camada 6.7). Renderiza, no topo do conteúdo, cada anúncio ativo
 * não-dispensado pelo usuário. Cores por severidade; X dispensa (quando dismissable) e some
 * via invalidação da query. Refetch a cada 5 min (anúncios não são tempo-real). Some por
 * completo quando não há anúncios — não ocupa espaço.
 */
export function AnnouncementBanner() {
  const qc = useQueryClient()
  const { data } = useQuery({
    queryKey: ['my-announcements'],
    queryFn: getMyAnnouncements,
    refetchInterval: 5 * 60 * 1000,
  })

  const dismiss = useMutation({
    mutationFn: (id: string) => dismissAnnouncement(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-announcements'] }),
  })

  const items = data?.items ?? []
  if (items.length === 0) return null

  return (
    <div className="space-y-2 px-4 pt-4 md:px-8">
      {items.map((a: MyAnnouncement) => (
        <div
          key={a.id}
          className={`flex items-start justify-between gap-3 rounded-lg border px-4 py-3 text-sm ${SEVERITY_STYLES[a.severity]}`}
          role="status"
        >
          <div className="min-w-0">
            <p className="font-medium">{a.title}</p>
            <p className="mt-0.5 opacity-90">{a.body}</p>
          </div>
          {a.dismissable && (
            <button
              type="button"
              onClick={() => dismiss.mutate(a.id)}
              disabled={dismiss.isPending}
              aria-label="Dispensar aviso"
              className="shrink-0 rounded p-1 opacity-70 hover:opacity-100"
            >
              <X className="size-4" />
            </button>
          )}
        </div>
      ))}
    </div>
  )
}
