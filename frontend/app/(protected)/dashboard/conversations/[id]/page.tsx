'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import {
  getConversation,
  updateConversationHandledBy,
  updateConversationStatus,
} from '@/lib/supabase/conversations'
import { getConversationMessages } from '@/lib/supabase/messages'

/**
 * Detalhe de uma conversa (SDK + RLS). Leitura: bolhas inbound/outbound, polling 5s.
 * Operação (4.7): trocar handled_by (ai ↔ human) e status (open ↔ closed) via UPDATE no
 * SDK — o RLS conversations_update (USING + WITH CHECK = company_id) garante que o tenant
 * só opera conversa da própria empresa.
 *
 * <p>Next 16: params é Promise — desembrulhado com use(). Guard de papel como nas outras
 * telas do tenant. Conversa de outro tenant → RLS faz getConversation lançar → erro.
 */
export default function ConversationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const router = useRouter()
  const queryClient = useQueryClient()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data: conversation, isError: convError } = useQuery({
    queryKey: ['conversation', id],
    queryFn: () => getConversation(id),
    enabled: isTenant,
    refetchInterval: 5000,
  })

  const { data: page, isPending, isError: msgError } = useQuery({
    queryKey: ['conversation-messages', id],
    queryFn: () => getConversationMessages(id, 50),
    enabled: isTenant,
    refetchInterval: 5000,
  })

  function invalidateConversation() {
    queryClient.invalidateQueries({ queryKey: ['conversation', id] })
    queryClient.invalidateQueries({ queryKey: ['my-conversations'] })
  }

  const handledByMutation = useMutation({
    mutationFn: (next: 'ai' | 'human') => updateConversationHandledBy(id, next),
    onSuccess: invalidateConversation,
    onError: (err) => console.error('updateConversationHandledBy failed:', err),
  })

  const statusMutation = useMutation({
    mutationFn: (next: 'open' | 'closed') => updateConversationStatus(id, next),
    onSuccess: invalidateConversation,
    onError: (err) => console.error('updateConversationStatus failed:', err),
  })

  const mutating = handledByMutation.isPending || statusMutation.isPending
  const mutationError = handledByMutation.isError || statusMutation.isError

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (convError || msgError) {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Conversa</h1>
        <p className="mb-4 text-sm text-destructive">
          Erro ao carregar a conversa (ou ela não pertence à sua empresa).
        </p>
        <Link href="/dashboard/conversations">
          <Button variant="outline">Voltar às conversas</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">
          {conversation?.contactName ?? conversation?.contactPhone ?? 'Conversa'}
        </h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard/conversations">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>

      {conversation && (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span>{conversation.contactPhone}</span>
            <Badge variant={conversation.status === 'open' ? 'success' : 'danger'}>
              {conversation.status}
            </Badge>
            <Badge variant={conversation.handledBy === 'ai' ? 'default' : 'warning'}>
              {conversation.handledBy}
            </Badge>
          </div>

          {/* Ações (4.7): trocar atendente e abrir/fechar */}
          <div className="mb-4 flex flex-wrap gap-2">
            {conversation.handledBy === 'ai' ? (
              <Button
                variant="outline"
                disabled={mutating}
                onClick={() => handledByMutation.mutate('human')}
              >
                Atender com humano
              </Button>
            ) : (
              <Button
                variant="outline"
                disabled={mutating}
                onClick={() => handledByMutation.mutate('ai')}
              >
                Devolver pra IA
              </Button>
            )}
            {conversation.status === 'open' ? (
              <Button
                variant="outline"
                disabled={mutating}
                onClick={() => statusMutation.mutate('closed')}
              >
                Fechar conversa
              </Button>
            ) : (
              <Button
                variant="outline"
                disabled={mutating}
                onClick={() => statusMutation.mutate('open')}
              >
                Reabrir conversa
              </Button>
            )}
          </div>

          {mutationError && (
            <p className="mb-3 text-sm text-destructive">
              Erro ao atualizar a conversa. Tente novamente.
            </p>
          )}
        </>
      )}

      {page && page.total > page.messages.length && (
        <p className="mb-2 text-xs text-muted-foreground">
          Mostrando últimas {page.messages.length} de {page.total} mensagens.
        </p>
      )}

      <div className="space-y-2 rounded-xl border border-border p-4">
        {isPending && <p className="text-sm text-muted-foreground">Carregando…</p>}
        {page?.messages.length === 0 && (
          <p className="text-sm text-muted-foreground">Nenhuma mensagem nesta conversa.</p>
        )}
        {page?.messages.map((m) => {
          const isInbound = m.direction === 'inbound'
          return (
            <div key={m.id} className={`flex ${isInbound ? 'justify-start' : 'justify-end'}`}>
              <div
                className={`max-w-[75%] rounded-lg px-3 py-2 text-sm ${
                  isInbound ? 'bg-muted' : 'bg-primary text-primary-foreground'
                }`}
              >
                <p>{m.content}</p>
                <p
                  className={`mt-1 text-[10px] ${
                    isInbound ? 'text-muted-foreground' : 'text-primary-foreground/70'
                  }`}
                >
                  {m.sender} · {new Date(m.createdAt).toLocaleString('pt-BR')}
                </p>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
