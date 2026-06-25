'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { listArtisans } from '@/lib/api/atelie/artisans'
import {
  addFitting,
  addItem,
  deleteFitting,
  deleteItem,
  getProposal,
  listProposals,
  openProposal,
  reorderFittings,
  transitionFitting,
  updateProposalStatus,
} from '@/lib/api/atelie/proposals'
import {
  ALLOWED_NEXT,
  ATELIE_PROPOSAL_STATUSES,
  ITEMS_LOCKED,
  statusLabel,
  type AtelieProposalStatusId,
} from '@/profiles/atelie/atelie-proposal-status'
import {
  ATELIE_PROJECT_TYPES,
  typeLabel,
  type AtelieProjectTypeId,
} from '@/profiles/atelie/atelie-project-type'
import {
  formatBrl,
  formatDate,
  type AtelieFitting,
  type AtelieProposal,
} from '@/profiles/atelie/atelie-types'

function StatusBadge({ status }: { status: AtelieProposalStatusId }) {
  const variant =
    status === 'aprovada' || status === 'fechada' ? 'success'
    : status === 'realizada' ? 'info'
    : status === 'orcada' ? 'warning'
    : status === 'recusada' || status === 'cancelada' ? 'muted'
    : 'default'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

function TypeBadge({ type }: { type: AtelieProjectTypeId }) {
  return <Badge variant="info">{typeLabel(type)}</Badge>
}

type OpenForm = {
  customerName: string; artisanId: string; projectType: AtelieProjectTypeId
  occasion: string; estimatedDate: string; briefing: string; notes: string
}
const EMPTY_OPEN: OpenForm = {
  customerName: '', artisanId: '', projectType: 'costura', occasion: '', estimatedDate: '', briefing: '', notes: '',
}

type ItemForm = { description: string; quantity: string; price: string }
const EMPTY_ITEM: ItemForm = { description: '', quantity: '1', price: '' }

type FittingForm = { title: string; dueDate: string; description: string }
const EMPTY_FITTING: FittingForm = { title: '', dueDate: '', description: '' }

/**
 * Propostas de ateliê do AtelieBot (camada 8.14). Clona o EventosBot: lista por status, abre proposta
 * (Modal), detalhe com DOIS editores inline: (a) ORÇAMENTO (total recalculado pelo backend a cada
 * mutação) e (b) PROVAS/AJUSTES (etapas com título+prazo, ordenadas por position, com toggle
 * pendente↔realizada e reordenação ↑↓ — a escapada da SM). Botões de transição de status
 * (ALLOWED_NEXT). Orçar exige ≥1 item de orçamento (400 empty_budget). Em estados travados os
 * editores somem (409 proposal_locked defensivo).
 */
export default function AtelieProposalsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [openModal, setOpenModal] = useState(false)
  const [openForm, setOpenForm] = useState<OpenForm>(EMPTY_OPEN)
  const [openError, setOpenError] = useState<string | null>(null)

  const [detailId, setDetailId] = useState<string | null>(null)
  const [itemForm, setItemForm] = useState<ItemForm>(EMPTY_ITEM)
  const [itemError, setItemError] = useState<string | null>(null)
  const [fittingForm, setFittingForm] = useState<FittingForm>(EMPTY_FITTING)
  const [fittingError, setFittingError] = useState<string | null>(null)
  const [statusTarget, setStatusTarget] = useState<AtelieProposalStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['atelie-proposals', status, page],
    queryFn: () => listProposals({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const artisans = useQuery({ queryKey: ['atelie-artisans-all'], queryFn: () => listArtisans({ onlyActive: true }) })

  const detail = useQuery({
    queryKey: ['atelie-proposal', detailId],
    queryFn: () => getProposal(detailId as string),
    enabled: detailId !== null,
  })

  const openMutation = useMutation({
    mutationFn: () => openProposal({
      customerName: openForm.customerName || null,
      artisanId: openForm.artisanId || null,
      projectType: openForm.projectType,
      occasion: openForm.occasion || null,
      estimatedDate: openForm.estimatedDate || null,
      briefing: openForm.briefing || null,
      notes: openForm.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['atelie-proposals'] })
      setOpenModal(false); setOpenForm(EMPTY_OPEN); setOpenError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'inactive_artisan') setOpenError('Esse artesão está inativo.')
      else if (e instanceof ApiError && e.reason === 'invalid_date') setOpenError('Data prevista inválida.')
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
      qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['atelie-proposals'] })
      setItemForm(EMPTY_ITEM); setItemError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setItemError('Esta proposta não aceita mais alteração de itens.')
      else setItemError('Erro ao adicionar o item.')
    },
  })

  const deleteItemMutation = useMutation({
    mutationFn: (itemId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteItem(detailId, itemId)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['atelie-proposals'] })
    },
  })

  const addFittingMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return addFitting(detailId, {
        title: fittingForm.title,
        dueDate: fittingForm.dueDate || null,
        description: fittingForm.description || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] })
      setFittingForm(EMPTY_FITTING); setFittingError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setFittingError('Esta proposta não aceita mais alteração de provas/ajustes.')
      else if (e instanceof ApiError && e.reason === 'invalid_date') setFittingError('Prazo inválido.')
      else setFittingError('Erro ao adicionar a prova/ajuste.')
    },
  })

  const deleteFittingMutation = useMutation({
    mutationFn: (fittingId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteFitting(detailId, fittingId)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] }),
  })

  const transitionFittingMutation = useMutation({
    mutationFn: (f: AtelieFitting) => {
      if (!detailId) throw new Error('sem proposta')
      return transitionFitting(detailId, f.id, f.status === 'realizada' ? 'pendente' : 'realizada')
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setFittingError('Esta proposta não aceita mais alteração de provas/ajustes.')
      else if (e instanceof ApiError && e.reason === 'invalid_fitting_status') setFittingError('Status de prova/ajuste inválido.')
      else setFittingError('Erro ao atualizar a prova/ajuste.')
    },
  })

  const reorderFittingMutation = useMutation({
    mutationFn: (orderedIds: string[]) => {
      if (!detailId) throw new Error('sem proposta')
      return reorderFittings(detailId, orderedIds)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setFittingError('Esta proposta não aceita mais reordenação.')
      else setFittingError('Erro ao reordenar.')
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: AtelieProposalStatusId) => {
      if (!detailId) throw new Error('sem proposta')
      return updateProposalStatus(detailId, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['atelie-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['atelie-proposals'] })
      setStatusTarget(null); setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'empty_budget') setStatusError('Adicione ao menos um item de orçamento antes de orçar.')
      else if (e instanceof ApiError && e.reason === 'invalid_status_transition') setStatusError('Transição de status inválida.')
      else setStatusError('Erro ao mudar o status.')
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))
  const p = detail.data
  const locked = p ? ITEMS_LOCKED[p.status] : true

  /** Move a prova/ajuste no índice `idx` para `idx+delta` e persiste a nova ordem. */
  function moveFitting(fittings: AtelieFitting[], idx: number, delta: number) {
    const target = idx + delta
    if (target < 0 || target >= fittings.length) return
    const ids = fittings.map((f) => f.id)
    const [moved] = ids.splice(idx, 1)
    ids.splice(target, 0, moved)
    setFittingError(null)
    reorderFittingMutation.mutate(ids)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Propostas"
        description="A IA abre a proposta pelo WhatsApp; a equipe orça e planeja as provas/ajustes aqui, e o cliente aprova pela conversa."
        actions={<Button onClick={() => { setOpenForm(EMPTY_OPEN); setOpenError(null); setOpenModal(true) }}>Nova proposta</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => { setStatus(''); setPage(0) }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todas
        </button>
        {ATELIE_PROPOSAL_STATUSES.map((s) => (
          <button key={s.id} onClick={() => { setStatus(s.id); setPage(0) }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}>
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
          {items.map((prop: AtelieProposal) => (
            <button key={prop.id} onClick={() => { setDetailId(prop.id); setItemError(null); setFittingError(null); setStatusError(null) }}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{prop.customerName}</span>
                  <TypeBadge type={prop.projectType} />
                  <StatusBadge status={prop.status} />
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {prop.estimatedDate ? formatDate(prop.estimatedDate) : 'prazo a definir'}
                  {prop.occasion ? ` · ${prop.occasion}` : ''}
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
          <span>Página {page + 1} de {totalPages} · {total} propostas</span>
          <div className="flex gap-1">
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page === 0}
              onClick={() => setPage((pg) => Math.max(0, pg - 1))}>←</Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page + 1 >= totalPages}
              onClick={() => setPage((pg) => pg + 1)}>→</Button>
          </div>
        </div>
      )}

      {/* Modal: nova proposta */}
      <Modal open={openModal} onClose={() => setOpenModal(false)} title="Nova proposta de ateliê" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); openMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
              <input value={openForm.customerName} onChange={(e) => setOpenForm((f) => ({ ...f, customerName: e.target.value }))} required
                placeholder="Nome do cliente"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Artesão (opcional)</label>
              <select value={openForm.artisanId} onChange={(e) => setOpenForm((f) => ({ ...f, artisanId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="">Sem atribuição</option>
                {(artisans.data?.items ?? []).map((ar) => (
                  <option key={ar.id} value={ar.id}>{ar.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Tipo de projeto</label>
              <select value={openForm.projectType}
                onChange={(e) => setOpenForm((f) => ({ ...f, projectType: e.target.value as AtelieProjectTypeId }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                {ATELIE_PROJECT_TYPES.map((t) => (
                  <option key={t.id} value={t.id}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Ocasião</label>
              <input value={openForm.occasion} onChange={(e) => setOpenForm((f) => ({ ...f, occasion: e.target.value }))}
                placeholder="casamento, formatura, presente…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Prazo previsto</label>
              <input type="date" value={openForm.estimatedDate} onChange={(e) => setOpenForm((f) => ({ ...f, estimatedDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Briefing</label>
            <textarea value={openForm.briefing} onChange={(e) => setOpenForm((f) => ({ ...f, briefing: e.target.value }))}
              rows={2} placeholder="O que o cliente imagina para a peça/projeto"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
            <textarea value={openForm.notes} onChange={(e) => setOpenForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {openError && <p className="text-sm text-destructive">{openError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setOpenModal(false)}>Cancelar</Button>
            <Button type="submit" disabled={openMutation.isPending}>
              {openMutation.isPending ? 'Abrindo…' : 'Abrir proposta'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + orçamento + provas/ajustes + status */}
      <Modal open={detailId !== null} onClose={() => { setDetailId(null); setStatusError(null); setItemError(null); setFittingError(null) }}
        title="Proposta de ateliê" size="lg">
        {detail.isPending || !p ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{p.customerName}</span>
              <TypeBadge type={p.projectType} />
              <StatusBadge status={p.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-xs text-muted-foreground">Telefone</dt><dd>{p.customerPhone ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Ocasião</dt><dd>{p.occasion ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Prazo previsto</dt><dd>{p.estimatedDate ? formatDate(p.estimatedDate) : '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Origem</dt><dd>{p.conversationId ? 'WhatsApp' : 'Manual'}</dd></div>
                {p.briefing && <div className="col-span-2"><dt className="text-xs text-muted-foreground">Briefing</dt><dd>{p.briefing}</dd></div>}
                {p.notes && <div className="col-span-2"><dt className="text-xs text-muted-foreground">Observações</dt><dd>{p.notes}</dd></div>}
              </dl>
            </Card>

            {/* Orçamento (entra no total) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Orçamento</h3>
                <span className="text-sm font-medium">Total: {formatBrl(p.totalCents)}</span>
              </div>
              {p.items.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum item de orçamento ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.items.map((it) => (
                    <div key={it.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <div className="min-w-0">
                        <span className="font-medium">{it.description}</span>
                        <span className="ml-2 text-xs text-muted-foreground">
                          {it.quantity} × {formatBrl(it.unitPriceCents)}
                        </span>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <span>{formatBrl(it.lineTotalCents)}</span>
                        {!locked && (
                          <Button variant="outline" className="h-6 px-2 text-xs"
                            disabled={deleteItemMutation.isPending} onClick={() => deleteItemMutation.mutate(it.id)}>
                            Remover
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {!locked && (
                <form className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => { e.preventDefault(); addItemMutation.mutate() }}>
                  <div className="flex-1 min-w-[8rem]">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Descrição</label>
                    <input value={itemForm.description} onChange={(e) => setItemForm((f) => ({ ...f, description: e.target.value }))} required
                      maxLength={200} placeholder="Tecido, mão de obra, bordado…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <div className="w-16">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Qtd</label>
                    <input type="number" min="1" value={itemForm.quantity}
                      onChange={(e) => setItemForm((f) => ({ ...f, quantity: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <div className="w-24">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Unit. (R$)</label>
                    <input type="number" min="0" step="0.01" value={itemForm.price} required
                      onChange={(e) => setItemForm((f) => ({ ...f, price: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <Button type="submit" className="h-8 px-3 text-xs" disabled={addItemMutation.isPending}>Adicionar</Button>
                </form>
              )}
              {itemError && <p className="text-sm text-destructive">{itemError}</p>}
            </div>

            {/* Provas/ajustes (NÃO entram no total — a escapada da SM) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Provas e ajustes</h3>
                <span className="text-xs text-muted-foreground">não entra no total</span>
              </div>
              {p.fittings.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhuma prova/ajuste planejada ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.fittings.map((ft, idx) => (
                    <div key={ft.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <div className="flex min-w-0 items-center gap-2">
                        <input type="checkbox" checked={ft.status === 'realizada'}
                          disabled={locked || transitionFittingMutation.isPending}
                          onChange={() => { setFittingError(null); transitionFittingMutation.mutate(ft) }} />
                        <div className="min-w-0">
                          <span className={`font-medium ${ft.status === 'realizada' ? 'text-muted-foreground line-through' : ''}`}>{ft.title}</span>
                          {ft.dueDate && <span className="ml-2 text-xs text-muted-foreground">prazo {formatDate(ft.dueDate)}</span>}
                          {ft.status === 'realizada' && ft.completedAt && (
                            <span className="ml-2 text-xs text-emerald-600">concluída {formatDate(ft.completedAt)}</span>
                          )}
                          {ft.description && <p className="text-xs text-muted-foreground">{ft.description}</p>}
                        </div>
                      </div>
                      {!locked && (
                        <div className="flex shrink-0 items-center gap-1">
                          <Button variant="outline" className="h-6 px-2 text-xs"
                            disabled={idx === 0 || reorderFittingMutation.isPending}
                            onClick={() => moveFitting(p.fittings, idx, -1)}>↑</Button>
                          <Button variant="outline" className="h-6 px-2 text-xs"
                            disabled={idx === p.fittings.length - 1 || reorderFittingMutation.isPending}
                            onClick={() => moveFitting(p.fittings, idx, 1)}>↓</Button>
                          <Button variant="outline" className="h-6 px-2 text-xs"
                            disabled={deleteFittingMutation.isPending} onClick={() => deleteFittingMutation.mutate(ft.id)}>
                            Remover
                          </Button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {!locked && (
                <form className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => { e.preventDefault(); addFittingMutation.mutate() }}>
                  <div className="flex-1 min-w-[8rem]">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Etapa</label>
                    <input value={fittingForm.title} onChange={(e) => setFittingForm((f) => ({ ...f, title: e.target.value }))} required
                      maxLength={200} placeholder="1ª prova, ajuste de barra, prova final…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <div className="w-36">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Prazo (opcional)</label>
                    <input type="date" value={fittingForm.dueDate}
                      onChange={(e) => setFittingForm((f) => ({ ...f, dueDate: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <Button type="submit" className="h-8 px-3 text-xs" disabled={addFittingMutation.isPending}>Adicionar</Button>
                </form>
              )}
              {fittingError && <p className="text-sm text-destructive">{fittingError}</p>}
            </div>

            {/* Status */}
            {ALLOWED_NEXT[p.status].length > 0 ? (
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Mudar status para…</label>
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[p.status].map((next) => (
                    <Button key={next} variant="outline" className="h-8 px-3 text-xs" onClick={() => setStatusTarget(next)}>
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
        onConfirm={() => { if (statusTarget) statusMutation.mutate(statusTarget) }}
      />
    </div>
  )
}
