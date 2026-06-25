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
import { listPlanners } from '@/lib/api/casamento/planners'
import {
  addChecklistTask,
  addItem,
  addTimelineItem,
  deleteChecklistTask,
  deleteItem,
  deleteTimelineItem,
  getProposal,
  listProposals,
  openProposal,
  toggleChecklistTask,
  updateProposalStatus,
} from '@/lib/api/casamento/proposals'
import {
  ALLOWED_NEXT,
  ITEMS_LOCKED,
  WEDDING_PROPOSAL_STATUSES,
  statusLabel,
  type WeddingProposalStatusId,
} from '@/profiles/casamento/wedding-proposal-status'
import {
  formatBrl,
  formatDate,
  formatTime,
  type WeddingChecklistTask,
  type WeddingProposal,
} from '@/profiles/casamento/casamento-types'

function StatusBadge({ status }: { status: WeddingProposalStatusId }) {
  const variant =
    status === 'aprovada' || status === 'fechada' ? 'success'
    : status === 'realizada' ? 'info'
    : status === 'orcada' ? 'warning'
    : status === 'recusada' || status === 'cancelada' ? 'muted'
    : 'default'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type OpenForm = {
  customerName: string; plannerId: string; weddingStyle: string
  weddingDate: string; guestCount: string; briefing: string; notes: string
}
const EMPTY_OPEN: OpenForm = {
  customerName: '', plannerId: '', weddingStyle: '', weddingDate: '', guestCount: '', briefing: '', notes: '',
}

type ItemForm = { description: string; quantity: string; price: string }
const EMPTY_ITEM: ItemForm = { description: '', quantity: '1', price: '' }

type MarkForm = { startTime: string; title: string; description: string }
const EMPTY_MARK: MarkForm = { startTime: '', title: '', description: '' }

type TaskForm = { title: string; dueDate: string; description: string }
const EMPTY_TASK: TaskForm = { title: '', dueDate: '', description: '' }

/**
 * Propostas de casamento do CasamentoBot (camada 8.7). Clona o EventosBot: lista por status, abre
 * proposta (Modal), detalhe com TRÊS editores inline: (a) ORÇAMENTO (total recalculado pelo backend a
 * cada mutação), (b) CRONOGRAMA (marcos horário+título, ordenados por horário, NÃO entram no total) e
 * (c) CHECKLIST pré-casamento (tarefas com título+prazo, ordenadas por prazo NULLS LAST, com checkbox
 * que marca/desmarca done — a escapada da SM). Botões de transição de status (ALLOWED_NEXT). Orçar
 * exige ≥1 item de orçamento (400 empty_budget). Em estados travados os editores somem (409
 * proposal_locked defensivo).
 */
export default function CasamentoProposalsPage() {
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
  const [taskForm, setTaskForm] = useState<TaskForm>(EMPTY_TASK)
  const [taskError, setTaskError] = useState<string | null>(null)
  const [statusTarget, setStatusTarget] = useState<WeddingProposalStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['casamento-proposals', status, page],
    queryFn: () => listProposals({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const planners = useQuery({ queryKey: ['casamento-planners-all'], queryFn: () => listPlanners({ onlyActive: true }) })

  const detail = useQuery({
    queryKey: ['casamento-proposal', detailId],
    queryFn: () => getProposal(detailId as string),
    enabled: detailId !== null,
  })

  const openMutation = useMutation({
    mutationFn: () => openProposal({
      customerName: openForm.customerName || null,
      plannerId: openForm.plannerId || null,
      weddingStyle: openForm.weddingStyle || null,
      weddingDate: openForm.weddingDate || null,
      guestCount: openForm.guestCount ? Math.max(0, Math.round(Number(openForm.guestCount))) : null,
      briefing: openForm.briefing || null,
      notes: openForm.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
      setOpenModal(false); setOpenForm(EMPTY_OPEN); setOpenError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'inactive_planner') setOpenError('Esse assessor está inativo.')
      else if (e instanceof ApiError && e.reason === 'invalid_date') setOpenError('Data do casamento inválida.')
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
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
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
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
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
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      setMarkForm(EMPTY_MARK); setMarkError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setMarkError('Esta proposta não aceita mais alteração de cronograma.')
      else if (e instanceof ApiError && (e.reason === 'invalid_time' || e.reason === 'timeline_item_not_found')) setMarkError('Horário inválido.')
      else setMarkError('Erro ao adicionar o marco.')
    },
  })

  const deleteMarkMutation = useMutation({
    mutationFn: (itemId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteTimelineItem(detailId, itemId)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] }),
  })

  const addTaskMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return addChecklistTask(detailId, {
        title: taskForm.title,
        dueDate: taskForm.dueDate || null,
        description: taskForm.description || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      setTaskForm(EMPTY_TASK); setTaskError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setTaskError('Esta proposta não aceita mais alteração do checklist.')
      else if (e instanceof ApiError && e.reason === 'invalid_date') setTaskError('Prazo inválido.')
      else setTaskError('Erro ao adicionar a tarefa.')
    },
  })

  const toggleTaskMutation = useMutation({
    mutationFn: (t: WeddingChecklistTask) => {
      if (!detailId) throw new Error('sem proposta')
      return toggleChecklistTask(detailId, t.id, !t.done)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked') setTaskError('Esta proposta não aceita mais alteração do checklist.')
      else if (e instanceof ApiError && e.reason === 'checklist_task_not_found') setTaskError('Tarefa não encontrada.')
      else setTaskError('Erro ao atualizar a tarefa.')
    },
  })

  const deleteTaskMutation = useMutation({
    mutationFn: (taskId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deleteChecklistTask(detailId, taskId)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] }),
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: WeddingProposalStatusId) => {
      if (!detailId) throw new Error('sem proposta')
      return updateProposalStatus(detailId, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
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

  return (
    <div className="space-y-6">
      <PageHeader
        title="Propostas"
        description="A IA abre a proposta pelo WhatsApp; a equipe orça, monta o cronograma e o checklist aqui, e o cliente aprova pela conversa."
        actions={<Button onClick={() => { setOpenForm(EMPTY_OPEN); setOpenError(null); setOpenModal(true) }}>Nova proposta</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => { setStatus(''); setPage(0) }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todas
        </button>
        {WEDDING_PROPOSAL_STATUSES.map((s) => (
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
          {items.map((prop: WeddingProposal) => (
            <button key={prop.id} onClick={() => { setDetailId(prop.id); setItemError(null); setMarkError(null); setTaskError(null); setStatusError(null) }}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{prop.customerName}</span>
                  {prop.weddingStyle && <span className="text-xs text-muted-foreground">{prop.weddingStyle}</span>}
                  <StatusBadge status={prop.status} />
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {prop.weddingDate ? formatDate(prop.weddingDate) : 'data a definir'}
                  {prop.guestCount != null ? ` · ${prop.guestCount} convidados` : ''}
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
      <Modal open={openModal} onClose={() => setOpenModal(false)} title="Nova proposta de casamento" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); openMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
              <input value={openForm.customerName} onChange={(e) => setOpenForm((f) => ({ ...f, customerName: e.target.value }))} required
                placeholder="Nome do cliente"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Assessor (opcional)</label>
              <select value={openForm.plannerId} onChange={(e) => setOpenForm((f) => ({ ...f, plannerId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="">Sem atribuição</option>
                {(planners.data?.items ?? []).map((pl) => (
                  <option key={pl.id} value={pl.id}>{pl.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Estilo do casamento</label>
              <input value={openForm.weddingStyle} onChange={(e) => setOpenForm((f) => ({ ...f, weddingStyle: e.target.value }))}
                placeholder="clássico, rústico, praia, mini wedding…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data do casamento</label>
              <input type="date" value={openForm.weddingDate} onChange={(e) => setOpenForm((f) => ({ ...f, weddingDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nº de convidados</label>
              <input type="number" min="0" value={openForm.guestCount} onChange={(e) => setOpenForm((f) => ({ ...f, guestCount: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Briefing</label>
            <textarea value={openForm.briefing} onChange={(e) => setOpenForm((f) => ({ ...f, briefing: e.target.value }))}
              rows={2} placeholder="O que o casal imagina para o casamento"
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

      {/* Modal: detalhe + orçamento + cronograma + checklist + status */}
      <Modal open={detailId !== null} onClose={() => { setDetailId(null); setStatusError(null); setItemError(null); setMarkError(null); setTaskError(null) }}
        title="Proposta de casamento" size="lg">
        {detail.isPending || !p ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{p.customerName}</span>
              {p.weddingStyle && <span className="text-xs text-muted-foreground">{p.weddingStyle}</span>}
              <StatusBadge status={p.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-xs text-muted-foreground">Telefone</dt><dd>{p.customerPhone ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Data</dt><dd>{p.weddingDate ? formatDate(p.weddingDate) : '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Convidados</dt><dd>{p.guestCount ?? '—'}</dd></div>
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
                      maxLength={200} placeholder="Buffet, decoração, fotografia…"
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

            {/* Cronograma (NÃO entra no total) */}
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
                    <div key={mk.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <div className="min-w-0">
                        <span className="font-mono text-xs font-medium">{formatTime(mk.startTime)}</span>
                        <span className="ml-2 font-medium">{mk.title}</span>
                        {mk.description && <span className="ml-2 text-xs text-muted-foreground">{mk.description}</span>}
                      </div>
                      {!locked && (
                        <Button variant="outline" className="h-6 px-2 text-xs"
                          disabled={deleteMarkMutation.isPending} onClick={() => deleteMarkMutation.mutate(mk.id)}>
                          Remover
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {!locked && (
                <form className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => { e.preventDefault(); addMarkMutation.mutate() }}>
                  <div className="w-24">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Horário</label>
                    <input type="time" value={markForm.startTime} required
                      onChange={(e) => setMarkForm((f) => ({ ...f, startTime: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <div className="flex-1 min-w-[8rem]">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Marco</label>
                    <input value={markForm.title} onChange={(e) => setMarkForm((f) => ({ ...f, title: e.target.value }))} required
                      maxLength={200} placeholder="Cerimônia, recepção, valsa, jantar…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <Button type="submit" className="h-8 px-3 text-xs" disabled={addMarkMutation.isPending}>Adicionar</Button>
                </form>
              )}
              {markError && <p className="text-sm text-destructive">{markError}</p>}
            </div>

            {/* Checklist pré-casamento (NÃO entra no total — a escapada da SM) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Checklist pré-casamento</h3>
                <span className="text-xs text-muted-foreground">não entra no total</span>
              </div>
              {p.checklist.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhuma tarefa no checklist ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {p.checklist.map((tk) => (
                    <div key={tk.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <div className="flex min-w-0 items-center gap-2">
                        <input type="checkbox" checked={tk.done}
                          disabled={locked || toggleTaskMutation.isPending}
                          onChange={() => { setTaskError(null); toggleTaskMutation.mutate(tk) }} />
                        <div className="min-w-0">
                          <span className={`font-medium ${tk.done ? 'text-muted-foreground line-through' : ''}`}>{tk.title}</span>
                          {tk.dueDate && <span className="ml-2 text-xs text-muted-foreground">prazo {formatDate(tk.dueDate)}</span>}
                          {tk.done && tk.doneAt && (
                            <span className="ml-2 text-xs text-emerald-600">concluída {formatDate(tk.doneAt)}</span>
                          )}
                          {tk.description && <p className="text-xs text-muted-foreground">{tk.description}</p>}
                        </div>
                      </div>
                      {!locked && (
                        <Button variant="outline" className="h-6 px-2 text-xs"
                          disabled={deleteTaskMutation.isPending} onClick={() => deleteTaskMutation.mutate(tk.id)}>
                          Remover
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {!locked && (
                <form className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => { e.preventDefault(); addTaskMutation.mutate() }}>
                  <div className="flex-1 min-w-[8rem]">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Tarefa</label>
                    <input value={taskForm.title} onChange={(e) => setTaskForm((f) => ({ ...f, title: e.target.value }))} required
                      maxLength={200} placeholder="Provar bolo, definir trajes, prova do vestido…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <div className="w-36">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Prazo (opcional)</label>
                    <input type="date" value={taskForm.dueDate}
                      onChange={(e) => setTaskForm((f) => ({ ...f, dueDate: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
                  </div>
                  <Button type="submit" className="h-8 px-3 text-xs" disabled={addTaskMutation.isPending}>Adicionar</Button>
                </form>
              )}
              {taskError && <p className="text-sm text-destructive">{taskError}</p>}
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
