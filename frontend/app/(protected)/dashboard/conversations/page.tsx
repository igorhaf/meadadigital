'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessagesSquare } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { TagChip } from '@/components/tag-color-picker'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { getAllConversationTags } from '@/lib/supabase/conversation-tags'
import {
  getMyConversations,
  setConversationMarkedUnread,
  type ConversationWithContact,
} from '@/lib/supabase/conversations'
import type { Tag } from '@/lib/supabase/tags'

/**
 * Classes do badge de agendamento (camada 5.15 #29) por urgência: amber (low),
 * orange (normal), red (high). Mesma família dos chips de tag (bg-X-100 / text-X-700).
 */
const SCHEDULING_BADGE_CLASSES: Record<'low' | 'normal' | 'high', string> = {
  low: 'bg-amber-100 text-amber-700',
  normal: 'bg-orange-100 text-orange-700',
  high: 'bg-red-100 text-red-700',
}

/**
 * Conversas da empresa do tenant (SDK + RLS), polling 5s. Super-admin não usa: redireciona
 * para /dashboard. Botão "Abrir" leva ao detalhe.
 *
 * Camada 5.14:
 *  - coluna "Tags" (#22): chips coloridos das tags aplicadas, via mapa agregado
 *    (getAllConversationTags, 1 query evita N+1);
 *  - ação "Marcar como não lida" / "Desmarcar" (#20): toggle de marked_unread via SDK,
 *    que alimenta o badge do menu (RPC count_unread_conversations, ramo OR marked_unread).
 */
export default function ConversationsPage() {
  const router = useRouter()
  const queryClient = useQueryClient()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-conversations'],
    queryFn: getMyConversations,
    enabled: isTenant,
    refetchInterval: 5000,
  })

  const { data: tagsByConv } = useQuery({
    queryKey: ['conversation-tags-all'],
    queryFn: getAllConversationTags,
    enabled: isTenant,
    refetchInterval: 5000,
  })

  // Toggle marcar/desmarcar não-lida (#20). Invalida a lista e o badge do menu no sucesso.
  const toggleUnread = useMutation({
    mutationFn: ({ id, markedUnread }: { id: string; markedUnread: boolean }) =>
      setConversationMarkedUnread(id, markedUnread),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-conversations'] })
      queryClient.invalidateQueries({ queryKey: ['unread-conversations-count'] })
    },
    onError: (err) => console.error('setConversationMarkedUnread failed:', err),
  })

  const columns: Column<ConversationWithContact>[] = [
    { key: 'contactName', header: 'Contato', render: (c) => c.contactName ?? '—' },
    { key: 'contactPhone', header: 'Telefone' },
    {
      key: 'status',
      header: 'Status',
      render: (c) => (
        <Badge variant={c.status === 'open' ? 'success' : 'danger'}>{c.status}</Badge>
      ),
    },
    {
      key: 'handledBy',
      header: 'Atendimento',
      render: (c) => (
        <Badge variant={c.handledBy === 'ai' ? 'default' : 'warning'}>{c.handledBy}</Badge>
      ),
    },
    {
      key: 'tags',
      header: 'Tags',
      render: (c) => {
        const tags: Tag[] = tagsByConv?.[c.id] ?? []
        if (
          tags.length === 0 &&
          !c.markedUnread &&
          !c.schedulingIntent &&
          !c.complaintIntent
        ) {
          return <span className="text-muted-foreground">—</span>
        }
        return (
          <div className="flex flex-wrap items-center gap-1">
            {c.markedUnread && <Badge variant="info">não lida</Badge>}
            {c.complaintIntent && (
              <span
                title={c.complaintIntent.rawExcerpt}
                className="inline-flex items-center rounded bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700"
              >
                ⚠️ reclamação
              </span>
            )}
            {c.schedulingIntent && (
              <span
                title={c.schedulingIntent.rawExcerpt}
                className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ${SCHEDULING_BADGE_CLASSES[c.schedulingIntent.urgency]}`}
              >
                🗓️ agendamento
              </span>
            )}
            {tags.map((t) => (
              <TagChip key={t.id} name={t.name} color={t.color} />
            ))}
          </div>
        )
      },
    },
    {
      key: 'lastMessageAt',
      header: 'Última atualização',
      render: (c) =>
        c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString('pt-BR') : '—',
    },
  ]

  // Estado vazio elevado (5.8): nenhuma conversa de todo.
  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-5xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (isError) {
    console.error('failed to load conversations:', error)
    return (
      <div className="mx-auto max-w-5xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Conversas</h1>
        <p className="mb-4 text-sm text-destructive">Erro ao carregar conversas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Conversas</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>
      {isEmpty ? (
        <EmptyState
          icon={<MessagesSquare />}
          title="Nenhuma conversa ainda"
          description="As conversas com seus clientes aparecerão aqui assim que eles mandarem mensagens pelo WhatsApp."
        />
      ) : (
        <DataTable<ConversationWithContact>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhuma conversa ainda. Mensagens aparecerão aqui quando seus clientes interagirem pelo WhatsApp."
          searchPlaceholder="Buscar por contato ou telefone…"
          searchFn={(c, q) =>
            `${c.contactName ?? ''} ${c.contactPhone}`.toLowerCase().includes(q)
          }
          actions={(c) => (
            <div className="flex items-center gap-1.5">
              <Link href={`/dashboard/conversations/${c.id}`}>
                <Button variant="outline" className="h-7 px-2 text-xs">
                  Abrir
                </Button>
              </Link>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={toggleUnread.isPending && toggleUnread.variables?.id === c.id}
                onClick={() =>
                  toggleUnread.mutate({ id: c.id, markedUnread: !c.markedUnread })
                }
              >
                {c.markedUnread ? 'Desmarcar' : 'Marcar como não lida'}
              </Button>
            </div>
          )}
        />
      )}
    </div>
  )
}
