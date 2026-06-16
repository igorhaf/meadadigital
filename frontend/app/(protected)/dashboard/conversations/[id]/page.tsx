'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { TagChip } from '@/components/tag-color-picker'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import { getMyTeams } from '@/lib/api/teams'
import {
  addTagToConversation,
  getConversationTags,
  removeTagFromConversation,
} from '@/lib/supabase/conversation-tags'
import {
  clearSchedulingIntent,
  getConversation,
  setConversationTeam,
  updateConversationHandledBy,
  updateConversationStatus,
} from '@/lib/supabase/conversations'
import { getConversationMessages } from '@/lib/supabase/messages'
import { getMyTags } from '@/lib/supabase/tags'

/** Classes do badge de urgência do agendamento (#29): amber/orange/red por nível. */
const SCHEDULING_URGENCY_CLASSES: Record<'low' | 'normal' | 'high', string> = {
  low: 'bg-amber-100 text-amber-700',
  normal: 'bg-orange-100 text-orange-700',
  high: 'bg-red-100 text-red-700',
}

/** Rótulo pt-BR da urgência exibido no badge da seção de agendamento. */
const SCHEDULING_URGENCY_LABEL: Record<'low' | 'normal' | 'high', string> = {
  low: 'sem pressa',
  normal: 'esta semana',
  high: 'urgente',
}

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

  // "Marcar como tratado" o agendamento (#29): zera scheduling_intent. invalidateConversation
  // faz a seção e o badge (lista) sumirem. Reusa o pattern das mutations acima.
  const clearIntentMutation = useMutation({
    mutationFn: () => clearSchedulingIntent(id),
    onSuccess: invalidateConversation,
    onError: (err) => console.error('clearSchedulingIntent failed:', err),
  })

  // Tags da conversa (#22): lista as aplicadas + o catálogo de tags da empresa (para o
  // autocomplete de adicionar). refetchInterval não é necessário aqui (tags mudam por
  // ação do tenant, não por evento externo) — invalidamos no sucesso das mutations.
  const { data: appliedTags } = useQuery({
    queryKey: ['conversation-tags', id],
    queryFn: () => getConversationTags(id),
    enabled: isTenant,
  })

  const { data: allTags } = useQuery({
    queryKey: ['my-tags'],
    queryFn: getMyTags,
    enabled: isTenant,
  })

  // Times da empresa (#76) para o seletor de atribuição. Mudam por ação do tenant na tela
  // de Times — sem polling; o seletor relê quando a query invalida.
  const { data: teams } = useQuery({
    queryKey: ['my-teams'],
    queryFn: getMyTeams,
    enabled: isTenant,
  })

  // Atribui/desatribui a conversa a um time (#76). String vazia no <select> → null.
  const teamMutation = useMutation({
    mutationFn: (teamId: string | null) => setConversationTeam(id, teamId),
    onSuccess: invalidateConversation,
    onError: (err) => console.error('setConversationTeam failed:', err),
  })

  function invalidateTags() {
    queryClient.invalidateQueries({ queryKey: ['conversation-tags', id] })
    queryClient.invalidateQueries({ queryKey: ['conversation-tags-all'] })
  }

  const addTag = useMutation({
    mutationFn: (tagId: string) => addTagToConversation(id, tagId),
    onSuccess: invalidateTags,
    onError: (err) => console.error('addTagToConversation failed:', err),
  })

  const removeTag = useMutation({
    mutationFn: (tagId: string) => removeTagFromConversation(id, tagId),
    onSuccess: invalidateTags,
    onError: (err) => console.error('removeTagFromConversation failed:', err),
  })

  // Tags do catálogo ainda não aplicadas — oferecidas no autocomplete.
  const appliedIds = new Set((appliedTags ?? []).map((t) => t.id))
  const availableTags = (allTags ?? []).filter((t) => !appliedIds.has(t.id))

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

          {/* Time (#76): atribui a conversa a um time/departamento. "Nenhum" desatribui. O
              dropdown só mostra times da própria empresa (getMyTeams). */}
          <div className="mb-4 flex items-center gap-2">
            <label htmlFor="conversation-team" className="text-sm text-muted-foreground">
              Time:
            </label>
            <select
              id="conversation-team"
              className="rounded-md border border-border px-3 py-1.5 text-sm"
              value={conversation.teamId ?? ''}
              disabled={teamMutation.isPending}
              onChange={(e) => teamMutation.mutate(e.target.value || null)}
            >
              <option value="">nenhum</option>
              {(teams ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>

          {/* Possível agendamento (#29): só renderiza quando a IA detectou intent.
              "Marcar como tratado" zera scheduling_intent → seção e badge somem. */}
          {conversation.schedulingIntent && (
            <div className="mb-4 rounded-xl border border-amber-300 bg-amber-50 p-4">
              <div className="mb-2 flex items-center justify-between">
                <h2 className="flex items-center gap-2 text-sm font-medium">
                  🗓️ Possível agendamento
                  <span
                    className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ${SCHEDULING_URGENCY_CLASSES[conversation.schedulingIntent.urgency]}`}
                  >
                    {SCHEDULING_URGENCY_LABEL[conversation.schedulingIntent.urgency]}
                  </span>
                </h2>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={clearIntentMutation.isPending}
                  onClick={() => clearIntentMutation.mutate()}
                >
                  Marcar como tratado
                </Button>
              </div>
              <dl className="space-y-1 text-sm">
                <div className="flex gap-2">
                  <dt className="text-muted-foreground">Quando:</dt>
                  <dd>{conversation.schedulingIntent.whenHint || 'não informado'}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="text-muted-foreground">Serviço:</dt>
                  <dd>{conversation.schedulingIntent.serviceHint || 'não informado'}</dd>
                </div>
              </dl>
              <p className="mt-2 rounded bg-white/60 px-3 py-2 text-sm italic text-muted-foreground">
                “{conversation.schedulingIntent.rawExcerpt}”
              </p>
              <p className="mt-2 text-xs text-muted-foreground">
                Detectado em{' '}
                {new Date(conversation.schedulingIntent.detectedAt).toLocaleString('pt-BR')}
              </p>
            </div>
          )}

          {/* Reclamação detectada (#52): box vermelho. A IA já forçou o handoff para humano
              quando detectou — aqui é só a sinalização visual ao atendente. */}
          {conversation.complaintIntent && (
            <div className="mb-4 rounded-xl border border-red-300 bg-red-50 p-4">
              <h2 className="mb-2 flex items-center gap-2 text-sm font-medium text-red-700">
                ⚠️ Reclamação detectada
              </h2>
              <p className="text-sm">{conversation.complaintIntent.summary}</p>
              <p className="mt-2 rounded bg-white/60 px-3 py-2 text-sm italic text-muted-foreground">
                “{conversation.complaintIntent.rawExcerpt}”
              </p>
              <p className="mt-2 text-xs text-muted-foreground">
                Detectado em{' '}
                {new Date(conversation.complaintIntent.detectedAt).toLocaleString('pt-BR')}
              </p>
            </div>
          )}

          {/* Cancelamento detectado (#51): box âmbar. */}
          {conversation.cancellationIntent && (
            <div className="mb-4 rounded-xl border border-amber-300 bg-amber-50 p-4">
              <h2 className="mb-2 flex items-center gap-2 text-sm font-medium text-amber-700">
                🚫 Cancelamento
              </h2>
              <p className="text-sm">{conversation.cancellationIntent.summary}</p>
              <p className="mt-2 rounded bg-white/60 px-3 py-2 text-sm italic text-muted-foreground">
                “{conversation.cancellationIntent.rawExcerpt}”
              </p>
              <p className="mt-2 text-xs text-muted-foreground">
                Detectado em{' '}
                {new Date(conversation.cancellationIntent.detectedAt).toLocaleString('pt-BR')}
              </p>
            </div>
          )}

          {/* Dados coletados (#53): pares chave/valor do extracted_data, quando não-vazio. */}
          {conversation.extractedData &&
            Object.keys(conversation.extractedData).length > 0 && (
              <div className="mb-4 rounded-xl border border-border p-4">
                <h2 className="mb-2 text-sm font-medium">Dados coletados</h2>
                <dl className="space-y-1 text-sm">
                  {Object.entries(conversation.extractedData).map(([key, value]) => (
                    <div key={key} className="flex gap-2">
                      <dt className="text-muted-foreground">{key}:</dt>
                      <dd>{String(value)}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            )}

          {/* Tags (#22): chips aplicados (com × para remover) + autocomplete para adicionar
              tag EXISTENTE (criar tag nova é só em /dashboard/tags). */}
          <div className="mb-4 rounded-xl border border-border p-4">
            <h2 className="mb-2 text-sm font-medium">Tags</h2>
            <div className="mb-3 flex flex-wrap items-center gap-1.5">
              {(appliedTags ?? []).length === 0 ? (
                <span className="text-sm text-muted-foreground">Nenhuma tag aplicada.</span>
              ) : (
                (appliedTags ?? []).map((t) => (
                  <TagChip
                    key={t.id}
                    name={t.name}
                    color={t.color}
                    onRemove={() => removeTag.mutate(t.id)}
                  />
                ))
              )}
            </div>
            {availableTags.length > 0 ? (
              <select
                className="w-full rounded-md border border-border px-3 py-2 text-sm"
                value=""
                disabled={addTag.isPending}
                onChange={(e) => {
                  if (e.target.value) {
                    addTag.mutate(e.target.value)
                  }
                }}
              >
                <option value="">+ Adicionar tag…</option>
                {availableTags.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            ) : (
              <p className="text-xs text-muted-foreground">
                {(allTags ?? []).length === 0 ? (
                  <>
                    Nenhuma tag cadastrada.{' '}
                    <Link href="/dashboard/tags" className="underline">
                      Criar tags
                    </Link>
                    .
                  </>
                ) : (
                  'Todas as tags já estão aplicadas.'
                )}
              </p>
            )}
          </div>
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
