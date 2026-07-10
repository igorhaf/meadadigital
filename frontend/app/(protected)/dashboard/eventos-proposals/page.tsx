'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { checkDate, listPackages } from '@/lib/api/eventos/packages'
import { listPlanners } from '@/lib/api/eventos/planners'
import {
  addItem,
  addTimelineItem,
  deleteItem,
  deleteTimelineItem,
  getProposal,
  listProposals,
  openProposal,
  updateProposalStatus,
} from '@/lib/api/eventos/proposals'
import {
  ALLOWED_NEXT,
  EVENT_PROPOSAL_STATUSES,
  ITEMS_LOCKED,
  statusLabel,
  type EventProposalStatusId,
} from '@/profiles/eventos/event-proposal-status'
import {
  formatDate,
  formatPrice,
  formatTime,
  type EventProposal,
} from '@/profiles/eventos/eventos-types'

function StatusBadge({ status }: { status: EventProposalStatusId }) {
  const variant =
    status === 'aprovada' || status === 'fechada'
      ? 'success'
      : status === 'realizada'
        ? 'info'
        : status === 'orcada'
          ? 'warning'
          : status === 'recusada' || status === 'cancelada'
            ? 'muted'
            : 'default'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type OpenForm = {
  customerName: string
  plannerId: string
  eventType: string
  eventDate: string
  guestCount: string
  briefing: string
  notes: string
}
const EMPTY_OPEN: OpenForm = {
  customerName: '',
  plannerId: '',
  eventType: '',
  eventDate: '',
  guestCount: '',
  briefing: '',
  notes: '',
}

type ItemForm = { description: string; quantity: string; price: string }
const EMPTY_ITEM: ItemForm = { description: '', quantity: '1', price: '' }

type MarkForm = { startTime: string; title: string; description: string }
const EMPTY_MARK: MarkForm = { startTime: '', title: '', description: '' }

/**
 * Propostas de evento do EventosBot (camada 8.2). Lista por status, abre proposta (Modal), detalhe
 * com DOIS editores inline: (a) ORÇAMENTO (total recalculado pelo backend a cada mutação) e (b)
 * CRONOGRAMA (marcos horário+título, ordenados por horário, NÃO entram no total — a escapada da SM).
 * Botões de transição de status (ALLOWED_NEXT). Orçar exige ≥1 item de orçamento (400 empty_budget).
 * Em estados travados os editores somem (409 proposal_locked defensivo).
 */
export default function EventosProposalsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [openModal, setOpenModal] = useState(false)
  const [openForm, setOpenForm] = useState<OpenForm>(EMPTY_OPEN)
  const [openError, setOpenError] = useState<string | null>(null)

  const [detailId, setDetailId] = useState<string | null>(null)
  const [itemForm, setItemForm] = useState<ItemForm>(EMPTY_ITEM)
  const [itemError, setItemError] = useState<string | null>(null)
  const [markForm, setMarkForm] = useState<MarkForm>(EMPTY_MARK)
  const [markError, setMarkError] = useState<string | null>(null)
  const [statusTarget, setStatusTarget] = useState<EventProposalStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['eventos-proposals', status, page],
    queryFn: () => listProposals({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const planners = useQuery({
    queryKey: ['eventos-planners-all'],
    queryFn: () => listPlanners({ onlyActive: true }),
  })

  const packages = useQuery({
    queryKey: ['eventos-packages'],
    queryFn: () => listPackages(),
  })

  // Onda 1 (backlog #3): aviso NÃO bloqueante de data ocupada ao escolher a data no modal.
  const dateCheck = useQuery({
    queryKey: ['eventos-date-check', openForm.eventDate],
    queryFn: () => checkDate(openForm.eventDate),
    enabled: openForm.eventDate !== '',
  })

  const detail = useQuery({
    queryKey: ['eventos-proposal', detailId],
    queryFn: () => getProposal(detailId as string),
    enabled: detailId !== null,
  })

  const openMutation = useMutation({
    mutationFn: () =>
      openProposal({
        customerName: openForm.customerName || null,
        plannerId: openForm.plannerId || null,
        eventType: openForm.eventType || null,
        eventDate: openForm.eventDate || null,
        guestCount: openForm.guestCount
          ? Math.max(0, Math.round(Number(openForm.guestCount)))
          : null,
        briefing: openForm.briefing || null,
        notes: openForm.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-proposals'] })
      setOpenModal(false)
      setOpenForm(EMPTY_OPEN)
      setOpenError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'inactive_planner')
        setOpenError('Esse cerimonialista está inativo.')
      else setOpenError('Erro ao abrir a proposta.')
    },
  })

  const addItemMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return addItem(detailId, {
        description: itemForm.description,
        quantity: Math.max(1, Math.round(Number(itemForm.quantity) || 1)),
        unitPriceCents: Math.round(Number(itemForm.price || 0) * 100),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['eventos-proposals'] })
      setItemForm(EMPTY_ITEM)
      setItemError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setItemError('Esta proposta não aceita mais alteração de itens.')
      else setItemError('Erro ao adicionar o item.')
    },
  })

  const deleteItemMutation = useMutation({
    mutationFn: (itemId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteItem(detailId, itemId)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['eventos-proposals'] })
    },
  })

  const addMarkMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return addTimelineItem(detailId, {
        startTime: markForm.startTime,
        title: markForm.title,
        description: markForm.description || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-proposal', detailId] })
      setMarkForm(EMPTY_MARK)
      setMarkError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setMarkError('Esta proposta não aceita mais alteração de cronograma.')
      else if (e instanceof ApiError && e.reason === 'invalid_time')
        setMarkError('Horário inválido.')
      else setMarkError('Erro ao adicionar o marco.')
    },
  })

  const deleteMarkMutation = useMutation({
    mutationFn: (itemId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteTimelineItem(detailId, itemId)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['eventos-proposal', detailId] }),
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: EventProposalStatusId) => {
      if (!detailId) throw new Error('sem proposta')
      return updateProposalStatus(detailId, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['eventos-proposals'] })
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'empty_budget')
        setStatusError('Adicione ao menos um item de orçamento antes de orçar.')
      else if (e instanceof ApiError && e.reason === 'invalid_status_transition')
        setStatusError('Transição de status inválida.')
      else setStatusError('Erro ao mudar o status.')
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))
  const p = detail.data
  const locked = p ? ITEMS_LOCKED[p.status] : true

  return (
    <div className="space-y-6">
      <PageHeader
        title="Propostas"
        description="A IA abre a proposta pelo WhatsApp; a equipe orça e monta o cronograma aqui, e o cliente aprova pela conversa."
        actions={
          <Button
            onClick={() => {
              setOpenForm(EMPTY_OPEN)
              setOpenError(null)
              setOpenModal(true)
            }}
          >
            Nova proposta
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <button
          onClick={() => {
            setStatus('')
            setPage(0)
          }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}
        >
          Todas
        </button>
        {EVENT_PROPOSAL_STATUSES.map((s) => (
          <button
            key={s.id}
            onClick={() => {
              setStatus(s.id)
              setPage(0)
            }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as propostas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma proposta encontrada.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((prop: EventProposal) => (
            <button
              key={prop.id}
              onClick={() => setDetailId(prop.id)}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{prop.customerName}</span>
                  {prop.eventType && (
                    <span className="text-xs text-muted-foreground">{prop.eventType}</span>
                  )}
                  <StatusBadge status={prop.status} />
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {prop.eventDate ? formatDate(prop.eventDate) : 'data a definir'}
                  {prop.guestCount != null ? ` · ${prop.guestCount} convidados` : ''}
                  {prop.plannerName ? ` · ${prop.plannerName}` : ''}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <div className="text-sm font-medium">{formatPrice(prop.totalCents)}</div>
                <div className="text-xs text-muted-foreground">{formatDate(prop.openedAt)}</div>
              </div>
            </button>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} propostas
          </span>
          <div className="flex gap-1">
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page === 0}
              onClick={() => setPage((pg) => Math.max(0, pg - 1))}
            >
              ←
            </Button>
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((pg) => pg + 1)}
            >
              →
            </Button>
          </div>
        </div>
      )}

      {/* Modal: nova proposta */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title="Nova proposta de evento"
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            openMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cliente
              </label>
              <input
                value={openForm.customerName}
                onChange={(e) => setOpenForm((f) => ({ ...f, customerName: e.target.value }))}
                required
                placeholder="Nome do cliente"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cerimonialista (opcional)
              </label>
              <select
                value={openForm.plannerId}
                onChange={(e) => setOpenForm((f) => ({ ...f, plannerId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Sem atribuição</option>
                {(planners.data?.items ?? []).map((pl) => (
                  <option key={pl.id} value={pl.id}>
                    {pl.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Tipo de evento
              </label>
              <input
                value={openForm.eventType}
                onChange={(e) => setOpenForm((f) => ({ ...f, eventType: e.target.value }))}
                placeholder="casamento, aniversário, corporativo…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Data prevista
              </label>
              <input
                type="date"
                value={openForm.eventDate}
                onChange={(e) => setOpenForm((f) => ({ ...f, eventDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
              {dateCheck.data?.occupied && (
                <p className="mt-1 text-xs font-medium text-amber-600">
                  ⚠ Já existe{' '}
                  {dateCheck.data.count > 1 ? `${dateCheck.data.count} eventos` : 'um evento'}{' '}
                  aprovado/fechado nesta data — confira antes de seguir.
                </p>
              )}
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Nº de convidados
              </label>
              <input
                type="number"
                min="0"
                value={openForm.guestCount}
                onChange={(e) => setOpenForm((f) => ({ ...f, guestCount: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Briefing</label>
            <textarea
              value={openForm.briefing}
              onChange={(e) => setOpenForm((f) => ({ ...f, briefing: e.target.value }))}
              rows={2}
              placeholder="O que o cliente imagina para o evento"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações
            </label>
            <textarea
              value={openForm.notes}
              onChange={(e) => setOpenForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {openError && <p className="text-sm text-destructive">{openError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setOpenModal(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={openMutation.isPending}>
              {openMutation.isPending ? 'Abrindo…' : 'Abrir proposta'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + orçamento + cronograma + status */}
      <Modal
        open={detailId !== null}
        onClose={() => {
          setDetailId(null)
          setStatusError(null)
          setItemError(null)
          setMarkError(null)
        }}
        title="Proposta de evento"
        size="lg"
      >
        {detail.isPending || !p ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{p.customerName}</span>
              {p.eventType && <span className="text-xs text-muted-foreground">{p.eventType}</span>}
              <StatusBadge status={p.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{p.customerPhone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Cerimonialista</dt>
                  <dd>{p.plannerName ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Data</dt>
                  <dd>{p.eventDate ? formatDate(p.eventDate) : '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Convidados</dt>
                  <dd>{p.guestCount ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Origem</dt>
                  <dd>{p.conversationId ? 'WhatsApp' : 'Manual'}</dd>
                </div>
                {p.briefing && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Briefing</dt>
                    <dd>{p.briefing}</dd>
                  </div>
                )}
                {p.notes && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Observações</dt>
                    <dd>{p.notes}</dd>
                  </div>
                )}
              </dl>
            </Card>

            {/* Orçamento (entra no total) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Orçamento</h3>
                <span className="text-sm font-medium">Total: {formatPrice(p.totalCents)}</span>
              </div>
              {p.items.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum item de orçamento ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.items.map((it) => (
                    <div
                      key={it.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="min-w-0">
                        <span className="font-medium">{it.description}</span>
                        <span className="ml-2 text-xs text-muted-foreground">
                          {it.quantity} × {formatPrice(it.unitPriceCents)}
                        </span>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <span>{formatPrice(it.lineTotalCents)}</span>
                        {!locked && (
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            disabled={deleteItemMutation.isPending}
                            onClick={() => deleteItemMutation.mutate(it.id)}
                          >
                            Remover
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {!locked && (
                <form
                  className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    addItemMutation.mutate()
                  }}
                >
                  {(packages.data?.items ?? []).filter((pk) => pk.active).length > 0 && (
                    <div className="w-full">
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">
                        Do catálogo (preenche descrição e preço)
                      </label>
                      <select
                        value=""
                        onChange={(e) => {
                          const pk = packages.data?.items.find((x) => x.id === e.target.value)
                          if (pk) {
                            setItemForm((f) => ({
                              ...f,
                              description: pk.name,
                              price: String(pk.priceCents / 100),
                            }))
                          }
                        }}
                        className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                      >
                        <option value="">Item avulso (digitar)…</option>
                        {(packages.data?.items ?? [])
                          .filter((pk) => pk.active)
                          .map((pk) => (
                            <option key={pk.id} value={pk.id}>
                              [{pk.kind}] {pk.name} — R$ {(pk.priceCents / 100).toFixed(2)}
                            </option>
                          ))}
                      </select>
                    </div>
                  )}
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Descrição
                    </label>
                    <input
                      value={itemForm.description}
                      onChange={(e) => setItemForm((f) => ({ ...f, description: e.target.value }))}
                      required
                      maxLength={200}
                      placeholder="Espaço, buffet, decoração…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-16">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Qtd
                    </label>
                    <input
                      type="number"
                      min="1"
                      value={itemForm.quantity}
                      onChange={(e) => setItemForm((f) => ({ ...f, quantity: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-24">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Unit. (R$)
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={itemForm.price}
                      required
                      onChange={(e) => setItemForm((f) => ({ ...f, price: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={addItemMutation.isPending}
                  >
                    Adicionar
                  </Button>
                </form>
              )}
              {itemError && <p className="text-sm text-destructive">{itemError}</p>}
            </div>

            {/* Cronograma (NÃO entra no total — a escapada da SM) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Cronograma do dia</h3>
                <span className="text-xs text-muted-foreground">não entra no total</span>
              </div>
              {p.timeline.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum marco no cronograma ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.timeline.map((mk) => (
                    <div
                      key={mk.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="min-w-0">
                        <span className="font-mono text-xs font-medium">
                          {formatTime(mk.startTime)}
                        </span>
                        <span className="ml-2 font-medium">{mk.title}</span>
                        {mk.description && (
                          <span className="ml-2 text-xs text-muted-foreground">
                            {mk.description}
                          </span>
                        )}
                      </div>
                      {!locked && (
                        <Button
                          variant="outline"
                          className="h-6 px-2 text-xs"
                          disabled={deleteMarkMutation.isPending}
                          onClick={() => deleteMarkMutation.mutate(mk.id)}
                        >
                          Remover
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {!locked && (
                <form
                  className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    addMarkMutation.mutate()
                  }}
                >
                  <div className="w-24">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Horário
                    </label>
                    <input
                      type="time"
                      value={markForm.startTime}
                      required
                      onChange={(e) => setMarkForm((f) => ({ ...f, startTime: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Marco
                    </label>
                    <input
                      value={markForm.title}
                      onChange={(e) => setMarkForm((f) => ({ ...f, title: e.target.value }))}
                      required
                      maxLength={200}
                      placeholder="Recepção, cerimônia, jantar, bolo…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={addMarkMutation.isPending}
                  >
                    Adicionar
                  </Button>
                </form>
              )}
              {markError && <p className="text-sm text-destructive">{markError}</p>}
            </div>

            {/* Status */}
            {ALLOWED_NEXT[p.status].length > 0 ? (
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Mudar status para…
                </label>
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[p.status].map((next) => (
                    <Button
                      key={next}
                      variant="outline"
                      className="h-8 px-3 text-xs"
                      onClick={() => setStatusTarget(next)}
                    >
                      {statusLabel(next)}
                    </Button>
                  ))}
                </div>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">Esta proposta está num status final.</p>
            )}
            {statusError && <p className="text-sm text-destructive">{statusError}</p>}
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O cliente é notificado automaticamente em orçamento, aprovação, fechamento e recusa (se houver vínculo com o WhatsApp)."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusTarget) statusMutation.mutate(statusTarget)
        }}
      />
    </div>
  )
}
