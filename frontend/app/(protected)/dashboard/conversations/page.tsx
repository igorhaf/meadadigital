'use client'

import { useQuery } from '@tanstack/react-query'
import { MessagesSquare } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { getMyConversations, type ConversationWithContact } from '@/lib/supabase/conversations'

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
    key: 'lastMessageAt',
    header: 'Última atualização',
    render: (c) =>
      c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString('pt-BR') : '—',
  },
]

/**
 * Conversas da empresa do tenant (SDK + RLS), só leitura, com polling 5s (decisão macro
 * 6 da camada 4). Super-admin não usa: redireciona para /dashboard. Botão "Abrir" na
 * coluna de ações leva ao detalhe /dashboard/conversations/[id] (a DataTable não tem
 * onRowClick; o botão é a via idiomática do componente).
 */
export default function ConversationsPage() {
  const router = useRouter()

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

  // Estado vazio elevado (5.8): nenhuma conversa de todo. Não confundir com filtro de
  // busca sem resultado — esse fica a cargo da DataTable (que segue renderizada).
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
            <Link href={`/dashboard/conversations/${c.id}`}>
              <Button variant="outline" className="h-7 px-2 text-xs">
                Abrir
              </Button>
            </Link>
          )}
        />
      )}
    </div>
  )
}
