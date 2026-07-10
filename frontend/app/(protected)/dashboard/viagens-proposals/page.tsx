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
import { listConsultants } from '@/lib/api/viagens/consultants'
import {
  addItem,
  addItineraryDay,
  deleteItem,
  deleteItineraryDay,
  getProposal,
  listProposals,
  openProposal,
  reorderItineraryDays,
  updateDeposit,
  updateProposalStatus,
} from '@/lib/api/viagens/proposals'
import { useOnSync } from '@/lib/use-synced-form'
import {
  ALLOWED_NEXT,
  ITEMS_LOCKED,
  statusLabel,
  TRAVEL_PROPOSAL_STATUSES,
  type TravelProposalStatusId,
} from '@/profiles/viagens/travel-proposal-status'
import {
  categoryLabel,
  formatBrl,
  formatDate,
  ITEM_CATEGORIES,
  type ItemCategoryId,
  type ItineraryDay,
  type Proposal,
} from '@/profiles/viagens/viagens-types'

function StatusBadge({ status }: { status: TravelProposalStatusId }) {
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
  consultantId: string
  destination: string
  startDate: string
  endDate: string
  numTravelers: string
  travelStyle: string
  briefing: string
  notes: string
}
const EMPTY_OPEN: OpenForm = {
  customerName: '',
  consultantId: '',
  destination: '',
  startDate: '',
  endDate: '',
  numTravelers: '',
  travelStyle: '',
  briefing: '',
  notes: '',
}

type ItemForm = { category: ItemCategoryId; description: string; quantity: string; price: string }
const EMPTY_ITEM: ItemForm = { category: 'aereo', description: '', quantity: '1', price: '' }

type DayForm = { dayDate: string; title: string; description: string }
const EMPTY_DAY: DayForm = { dayDate: '', title: '', description: '' }

/**
 * Propostas de viagem do ViagensBot (camada 8.18). Clona o EventosBot/AtelieBot: lista por status,
 * abre proposta (Modal), detalhe com DOIS editores inline: (a) COTAÇÃO (itens com categoria
 * aereo/hospedagem/traslado/passeio/outro; total recalculado pelo backend a cada mutação) e (b)
 * ITINERÁRIO dia-a-dia (cada linha = um DIA com data+título+descrição, ordenado por dayNumber, com
 * reordenação ↑↓ que recomputa dayNumber — a escapada da SM, NÃO entra no total). Botões de transição
 * de status (ALLOWED_NEXT). Orçar exige ≥1 item de cotação (400 empty_budget). Em estados travados os
 * editores somem (409 proposal_locked defensivo).
 */
export default function ViagensProposalsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [openModal, setOpenModal] = useState(false)
  const [openForm, setOpenForm] = useState<OpenForm>(EMPTY_OPEN)
  const [openError, setOpenError] = useState<string | null>(null)

  const [detailId, setDetailId] = useState<string | null>(null)
  const [itemForm, setItemForm] = useState<ItemForm>(EMPTY_ITEM)
  const [itemError, setItemError] = useState<string | null>(null)
  const [dayForm, setDayForm] = useState<DayForm>(EMPTY_DAY)
  const [dayError, setDayError] = useState<string | null>(null)
  const [statusTarget, setStatusTarget] = useState<TravelProposalStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)
  const [depositForm, setDepositForm] = useState<{ value: string; paid: boolean }>({
    value: '',
    paid: false,
  })
  const [depositError, setDepositError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['viagens-proposals', status, page],
    queryFn: () => listProposals({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const consultants = useQuery({
    queryKey: ['viagens-consultants-all'],
    queryFn: () => listConsultants({ onlyActive: true }),
  })

  const detail = useQuery({
    queryKey: ['viagens-proposal', detailId],
    queryFn: () => getProposal(detailId as string),
    enabled: detailId !== null,
  })

  const openMutation = useMutation({
    mutationFn: () =>
      openProposal({
        customerName: openForm.customerName || null,
        consultantId: openForm.consultantId || null,
        destination: openForm.destination || null,
        startDate: openForm.startDate || null,
        endDate: openForm.endDate || null,
        numTravelers: openForm.numTravelers
          ? Math.max(0, Math.round(Number(openForm.numTravelers)))
          : null,
        travelStyle: openForm.travelStyle || null,
        briefing: openForm.briefing || null,
        notes: openForm.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-proposals'] })
      setOpenModal(false)
      setOpenForm(EMPTY_OPEN)
      setOpenError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'inactive_consultant')
        setOpenError('Esse consultor está inativo.')
      else if (e instanceof ApiError && e.reason === 'invalid_date') setOpenError('Data inválida.')
      else setOpenError('Erro ao abrir a proposta.')
    },
  })

  const addItemMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return addItem(detailId, {
        category: itemForm.category,
        description: itemForm.description,
        quantity: Math.max(1, Math.round(Number(itemForm.quantity) || 1)),
        unitPriceCents: Math.round(Number(itemForm.price || 0) * 100),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['viagens-proposals'] })
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
      qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['viagens-proposals'] })
    },
  })

  const addDayMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return addItineraryDay(detailId, {
        dayDate: dayForm.dayDate || null,
        title: dayForm.title,
        description: dayForm.description || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] })
      setDayForm(EMPTY_DAY)
      setDayError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setDayError('Esta proposta não aceita mais alteração do itinerário.')
      else if (e instanceof ApiError && e.reason === 'invalid_date') setDayError('Data inválida.')
      else setDayError('Erro ao adicionar o dia.')
    },
  })

  const deleteDayMutation = useMutation({
    mutationFn: (dayId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteItineraryDay(detailId, dayId)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] }),
  })

  const reorderDayMutation = useMutation({
    mutationFn: (orderedIds: string[]) => {
      if (!detailId) throw new Error('sem proposta')
      return reorderItineraryDays(detailId, orderedIds)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setDayError('Esta proposta não aceita mais reordenação.')
      else setDayError('Erro ao reordenar.')
    },
  })

  const depositMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      const cents = depositForm.value === '' ? null : Math.round(Number(depositForm.value) * 100)
      return updateDeposit(detailId, { depositCents: cents, depositPaid: depositForm.paid })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['viagens-proposals'] })
      setDepositError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_deposit')
        setDepositError('Para marcar como recebido, informe um valor de sinal maior que zero.')
      else if (e instanceof ApiError && e.reason === 'proposal_locked')
        setDepositError('Esta proposta não aceita mais alteração do sinal.')
      else setDepositError('Erro ao salvar o sinal.')
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: TravelProposalStatusId) => {
      if (!detailId) throw new Error('sem proposta')
      return updateProposalStatus(detailId, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['viagens-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['viagens-proposals'] })
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'empty_budget')
        setStatusError('Adicione ao menos um item de cotação antes de orçar.')
      else if (e instanceof ApiError && e.reason === 'deposit_required')
        setStatusError('Sinal registrado e não recebido — confirme o recebimento antes de fechar.')
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

  useOnSync(p?.id, () => {
    if (!p) return
    setDepositForm({
      value: p.depositCents != null ? String(p.depositCents / 100) : '',
      paid: p.depositPaid,
    })
    setDepositError(null)
  })

  /** Move o dia no índice `idx` para `idx+delta` e persiste a nova ordem (recomputa dayNumber). */
  function moveDay(days: ItineraryDay[], idx: number, delta: number) {
    const target = idx + delta
    if (target < 0 || target >= days.length) return
    const ids = days.map((d) => d.id)
    const [moved] = ids.splice(idx, 1)
    ids.splice(target, 0, moved)
    setDayError(null)
    reorderDayMutation.mutate(ids)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Propostas"
        description="A IA abre a proposta pelo WhatsApp; a equipe monta a cotação e o itinerário aqui, e o cliente aprova pela conversa."
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
        {TRAVEL_PROPOSAL_STATUSES.map((s) => (
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
          {items.map((prop: Proposal) => (
            <button
              key={prop.id}
              onClick={() => {
                setDetailId(prop.id)
                setItemError(null)
                setDayError(null)
                setStatusError(null)
              }}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{prop.customerName}</span>
                  {prop.destination && (
                    <span className="text-xs text-muted-foreground">{prop.destination}</span>
                  )}
                  <StatusBadge status={prop.status} />
                  {prop.depositCents != null && prop.depositCents > 0 && !prop.depositPaid && (
                    <Badge variant="warning">Sinal pendente</Badge>
                  )}
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {prop.startDate ? formatDate(prop.startDate) : 'datas a definir'}
                  {prop.endDate ? ` – ${formatDate(prop.endDate)}` : ''}
                  {prop.numTravelers != null ? ` · ${prop.numTravelers} viajantes` : ''}
                  {prop.consultantName ? ` · ${prop.consultantName}` : ''}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <div className="text-sm font-medium">{formatBrl(prop.totalCents)}</div>
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
        title="Nova proposta de viagem"
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
                Consultor (opcional)
              </label>
              <select
                value={openForm.consultantId}
                onChange={(e) => setOpenForm((f) => ({ ...f, consultantId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Sem atribuição</option>
                {(consultants.data?.items ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Destino
              </label>
              <input
                value={openForm.destination}
                onChange={(e) => setOpenForm((f) => ({ ...f, destination: e.target.value }))}
                placeholder="Paris, Cancún, Nordeste…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Estilo de viagem
              </label>
              <input
                value={openForm.travelStyle}
                onChange={(e) => setOpenForm((f) => ({ ...f, travelStyle: e.target.value }))}
                placeholder="lua de mel, família, mochilão…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Data de ida
              </label>
              <input
                type="date"
                value={openForm.startDate}
                onChange={(e) => setOpenForm((f) => ({ ...f, startDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Data de volta
              </label>
              <input
                type="date"
                value={openForm.endDate}
                onChange={(e) => setOpenForm((f) => ({ ...f, endDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Nº de viajantes
              </label>
              <input
                type="number"
                min="0"
                value={openForm.numTravelers}
                onChange={(e) => setOpenForm((f) => ({ ...f, numTravelers: e.target.value }))}
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
              placeholder="O que o cliente imagina para a viagem"
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

      {/* Modal: detalhe + cotação + itinerário + status */}
      <Modal
        open={detailId !== null}
        onClose={() => {
          setDetailId(null)
          setStatusError(null)
          setItemError(null)
          setDayError(null)
        }}
        title="Proposta de viagem"
        size="lg"
      >
        {detail.isPending || !p ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{p.customerName}</span>
              {p.destination && (
                <span className="text-xs text-muted-foreground">{p.destination}</span>
              )}
              <StatusBadge status={p.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{p.customerPhone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Consultor</dt>
                  <dd>{p.consultantName ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Destino</dt>
                  <dd>{p.destination ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Estilo</dt>
                  <dd>{p.travelStyle ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Período</dt>
                  <dd>
                    {p.startDate ? formatDate(p.startDate) : '—'}
                    {p.endDate ? ` – ${formatDate(p.endDate)}` : ''}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Viajantes</dt>
                  <dd>{p.numTravelers ?? '—'}</dd>
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

            {/* Cotação (entra no total) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Cotação</h3>
                <span className="text-sm font-medium">Total: {formatBrl(p.totalCents)}</span>
              </div>
              {p.items.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum item de cotação ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.items.map((it) => (
                    <div
                      key={it.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="min-w-0">
                        <Badge variant="info">{categoryLabel(it.category)}</Badge>
                        <span className="ml-2 font-medium">{it.description}</span>
                        <span className="ml-2 text-xs text-muted-foreground">
                          {it.quantity} × {formatBrl(it.unitPriceCents)}
                        </span>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <span>{formatBrl(it.lineTotalCents)}</span>
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
                  <div className="w-32">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Categoria
                    </label>
                    <select
                      value={itemForm.category}
                      onChange={(e) =>
                        setItemForm((f) => ({ ...f, category: e.target.value as ItemCategoryId }))
                      }
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    >
                      {ITEM_CATEGORIES.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Descrição
                    </label>
                    <input
                      value={itemForm.description}
                      onChange={(e) => setItemForm((f) => ({ ...f, description: e.target.value }))}
                      required
                      maxLength={200}
                      placeholder="Passagem GRU–CDG, hotel 4 noites…"
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

            {/* Itinerário dia-a-dia (NÃO entra no total — a escapada da SM) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Itinerário</h3>
                <span className="text-xs text-muted-foreground">não entra no total</span>
              </div>
              {p.itinerary.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum dia no itinerário ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.itinerary.map((day, idx) => (
                    <div
                      key={day.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="min-w-0">
                        <span className="font-mono text-xs font-medium">Dia {day.dayNumber}</span>
                        {day.dayDate && (
                          <span className="ml-2 text-xs text-muted-foreground">
                            {formatDate(day.dayDate)}
                          </span>
                        )}
                        <span className="ml-2 font-medium">{day.title}</span>
                        {day.description && (
                          <p className="text-xs text-muted-foreground">{day.description}</p>
                        )}
                      </div>
                      {!locked && (
                        <div className="flex shrink-0 items-center gap-1">
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            disabled={idx === 0 || reorderDayMutation.isPending}
                            onClick={() => moveDay(p.itinerary, idx, -1)}
                          >
                            ↑
                          </Button>
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            disabled={
                              idx === p.itinerary.length - 1 || reorderDayMutation.isPending
                            }
                            onClick={() => moveDay(p.itinerary, idx, 1)}
                          >
                            ↓
                          </Button>
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            disabled={deleteDayMutation.isPending}
                            onClick={() => deleteDayMutation.mutate(day.id)}
                          >
                            Remover
                          </Button>
                        </div>
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
                    addDayMutation.mutate()
                  }}
                >
                  <div className="w-36">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Data (opcional)
                    </label>
                    <input
                      type="date"
                      value={dayForm.dayDate}
                      onChange={(e) => setDayForm((f) => ({ ...f, dayDate: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Título do dia
                    </label>
                    <input
                      value={dayForm.title}
                      onChange={(e) => setDayForm((f) => ({ ...f, title: e.target.value }))}
                      required
                      maxLength={200}
                      placeholder="Chegada em Paris, City tour, Versalhes…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Descrição (opcional)
                    </label>
                    <input
                      value={dayForm.description}
                      onChange={(e) => setDayForm((f) => ({ ...f, description: e.target.value }))}
                      placeholder="O que está previsto no dia"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={addDayMutation.isPending}
                  >
                    Adicionar
                  </Button>
                </form>
              )}
              {dayError && <p className="text-sm text-destructive">{dayError}</p>}
            </div>

            {/* Sinal/entrada (onda backlog #1 — registro manual até o gateway #50) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Sinal / entrada</h3>
                {p.depositCents != null && p.depositCents > 0 && (
                  <Badge variant={p.depositPaid ? 'success' : 'warning'}>
                    {p.depositPaid ? 'Sinal recebido' : 'Sinal pendente'}
                  </Badge>
                )}
              </div>
              {locked ? (
                <p className="text-xs text-muted-foreground">
                  {p.depositCents != null && p.depositCents > 0
                    ? `Sinal de ${formatBrl(p.depositCents)} — ${p.depositPaid ? `recebido${p.depositPaidAt ? ` em ${formatDate(p.depositPaidAt)}` : ''}` : 'não recebido'}.`
                    : 'Sem sinal registrado.'}
                </p>
              ) : (
                <form
                  className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    depositMutation.mutate()
                  }}
                >
                  <div className="w-28">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Valor (R$)
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={depositForm.value}
                      onChange={(e) => setDepositForm((f) => ({ ...f, value: e.target.value }))}
                      placeholder="0,00"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <label className="flex h-8 items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={depositForm.paid}
                      onChange={(e) => setDepositForm((f) => ({ ...f, paid: e.target.checked }))}
                    />
                    Recebido
                  </label>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={depositMutation.isPending}
                  >
                    {depositMutation.isPending ? 'Salvando…' : 'Salvar sinal'}
                  </Button>
                  <p className="w-full text-xs text-muted-foreground">
                    Com sinal registrado e não recebido, o fechamento fica bloqueado até a equipe
                    confirmar o recebimento (Pix confirmado à mão até o pagamento online chegar).
                  </p>
                </form>
              )}
              {depositError && <p className="text-sm text-destructive">{depositError}</p>}
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
