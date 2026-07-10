'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { listCatalog } from '@/lib/api/casamento/catalog'
import {
  createPayment,
  deletePayment,
  listPayments,
  setPaymentPaid,
} from '@/lib/api/casamento/payments'
import { listPlanners } from '@/lib/api/casamento/planners'
import {
  addChecklistTask,
  addItem,
  addTimelineItem,
  applyCoupon,
  deleteChecklistTask,
  deleteItem,
  deleteTimelineItem,
  getProposal,
  listProposals,
  openProposal,
  removeCoupon,
  toggleChecklistTask,
  updateProposalStatus,
} from '@/lib/api/casamento/proposals'
import { ApiError } from '@/lib/api/client'
import { useOnSync } from '@/lib/use-synced-form'
import {
  formatBrl,
  formatDate,
  formatTime,
  type WeddingChecklistTask,
  type WeddingProposal,
} from '@/profiles/casamento/casamento-types'
import {
  ALLOWED_NEXT,
  ITEMS_LOCKED,
  statusLabel,
  WEDDING_PROPOSAL_STATUSES,
  type WeddingProposalStatusId,
} from '@/profiles/casamento/wedding-proposal-status'

function StatusBadge({ status }: { status: WeddingProposalStatusId }) {
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
  weddingStyle: string
  weddingDate: string
  guestCount: string
  briefing: string
  notes: string
}
const EMPTY_OPEN: OpenForm = {
  customerName: '',
  plannerId: '',
  weddingStyle: '',
  weddingDate: '',
  guestCount: '',
  briefing: '',
  notes: '',
}

type ItemForm = { description: string; quantity: string; price: string }
const EMPTY_ITEM: ItemForm = { description: '', quantity: '1', price: '' }

type MarkForm = { startTime: string; title: string; description: string }
const EMPTY_MARK: MarkForm = { startTime: '', title: '', description: '' }

type TaskForm = { title: string; dueDate: string; description: string }
const EMPTY_TASK: TaskForm = { title: '', dueDate: '', description: '' }

type PaymentForm = { kind: 'sinal' | 'parcela'; label: string; dueDate: string; amount: string }
const EMPTY_PAYMENT: PaymentForm = { kind: 'sinal', label: '', dueDate: '', amount: '' }

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
  const [couponCode, setCouponCode] = useState('')
  const [couponError, setCouponError] = useState<string | null>(null)
  const [paymentForm, setPaymentForm] = useState<PaymentForm>(EMPTY_PAYMENT)
  const [paymentError, setPaymentError] = useState<string | null>(null)
  const [statusTarget, setStatusTarget] = useState<WeddingProposalStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['casamento-proposals', status, page],
    queryFn: () => listProposals({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const planners = useQuery({
    queryKey: ['casamento-planners-all'],
    queryFn: () => listPlanners({ onlyActive: true }),
  })

  const catalog = useQuery({
    queryKey: ['casamento-catalog-active'],
    queryFn: () => listCatalog({ onlyActive: true }),
  })

  const detail = useQuery({
    queryKey: ['casamento-proposal', detailId],
    queryFn: () => getProposal(detailId as string),
    enabled: detailId !== null,
  })

  const payments = useQuery({
    queryKey: ['casamento-payments', detailId],
    queryFn: () => listPayments(detailId as string),
    enabled: detailId !== null,
  })

  const openMutation = useMutation({
    mutationFn: () =>
      openProposal({
        customerName: openForm.customerName || null,
        plannerId: openForm.plannerId || null,
        weddingStyle: openForm.weddingStyle || null,
        weddingDate: openForm.weddingDate || null,
        guestCount: openForm.guestCount
          ? Math.max(0, Math.round(Number(openForm.guestCount)))
          : null,
        briefing: openForm.briefing || null,
        notes: openForm.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
      setOpenModal(false)
      setOpenForm(EMPTY_OPEN)
      setOpenError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'inactive_planner')
        setOpenError('Esse assessor está inativo.')
      else if (e instanceof ApiError && e.reason === 'invalid_date')
        setOpenError('Data do casamento inválida.')
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
      setMarkForm(EMPTY_MARK)
      setMarkError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setMarkError('Esta proposta não aceita mais alteração de cronograma.')
      else if (
        e instanceof ApiError &&
        (e.reason === 'invalid_time' || e.reason === 'timeline_item_not_found')
      )
        setMarkError('Horário inválido.')
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
      setTaskForm(EMPTY_TASK)
      setTaskError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setTaskError('Esta proposta não aceita mais alteração do checklist.')
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
      if (e instanceof ApiError && e.reason === 'proposal_locked')
        setTaskError('Esta proposta não aceita mais alteração do checklist.')
      else if (e instanceof ApiError && e.reason === 'checklist_task_not_found')
        setTaskError('Tarefa não encontrada.')
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

  const applyCouponMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return applyCoupon(detailId, couponCode.trim())
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
      qc.invalidateQueries({ queryKey: ['casamento-coupons'] })
      setCouponCode('')
      setCouponError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_coupon')
        setCouponError(
          'Cupom inválido (inexistente, inativo, vencido, esgotado ou abaixo do orçamento mínimo).',
        )
      else if (e instanceof ApiError && e.reason === 'proposal_locked')
        setCouponError('Esta proposta não aceita mais alteração de cupom.')
      else setCouponError('Erro ao aplicar o cupom.')
    },
  })

  const removeCouponMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return removeCoupon(detailId)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
      qc.invalidateQueries({ queryKey: ['casamento-coupons'] })
      setCouponError(null)
    },
    onError: () => setCouponError('Erro ao remover o cupom.'),
  })

  const addPaymentMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem proposta')
      return createPayment(detailId, {
        kind: paymentForm.kind,
        label: paymentForm.label.trim() || null,
        dueDate: paymentForm.dueDate,
        amountCents: Math.round(Number(paymentForm.amount || 0) * 100),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-payments', detailId] })
      setPaymentForm(EMPTY_PAYMENT)
      setPaymentError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_payment')
        setPaymentError('Parcela inválida (valor > 0 e vencimento obrigatórios).')
      else if (e instanceof ApiError && e.reason === 'proposal_locked')
        setPaymentError('Proposta recusada/cancelada não aceita plano de pagamento.')
      else setPaymentError('Erro ao adicionar a parcela.')
    },
  })

  const setPaidMutation = useMutation({
    mutationFn: (args: { paymentId: string; paid: boolean }) => {
      if (!detailId) throw new Error('sem proposta')
      return setPaymentPaid(detailId, args.paymentId, args.paid)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-payments', detailId] })
      setPaymentError(null)
    },
    onError: () => setPaymentError('Erro ao atualizar o pagamento.'),
  })

  const deletePaymentMutation = useMutation({
    mutationFn: (paymentId: string) => {
      if (!detailId) throw new Error('sem proposta')
      return deletePayment(detailId, paymentId)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['casamento-payments', detailId] }),
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: WeddingProposalStatusId) => {
      if (!detailId) throw new Error('sem proposta')
      return updateProposalStatus(detailId, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-proposal', detailId] })
      qc.invalidateQueries({ queryKey: ['casamento-proposals'] })
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'empty_budget')
        setStatusError('Adicione ao menos um item de orçamento antes de orçar.')
      else if (e instanceof ApiError && e.reason === 'deposit_required')
        setStatusError(
          'O plano tem SINAL em aberto — marque o sinal como pago antes de fechar o contrato.',
        )
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
  // plano de pagamento segue editável após fechada (parcelas vencem até o casamento);
  // só recusada/cancelada travam.
  const paymentsLocked = p ? p.status === 'recusada' || p.status === 'cancelada' : true

  // reseta forms locais ao trocar de proposta (não a cada refetch — preserva digitação).
  useOnSync(p?.id, () => {
    setCouponCode('')
    setCouponError(null)
    setPaymentForm(EMPTY_PAYMENT)
    setPaymentError(null)
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Propostas"
        description="A IA abre a proposta pelo WhatsApp; a equipe orça, monta o cronograma e o checklist aqui, e o cliente aprova pela conversa."
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
        {WEDDING_PROPOSAL_STATUSES.map((s) => (
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
          {items.map((prop: WeddingProposal) => (
            <button
              key={prop.id}
              onClick={() => {
                setDetailId(prop.id)
                setItemError(null)
                setMarkError(null)
                setTaskError(null)
                setStatusError(null)
              }}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{prop.customerName}</span>
                  {prop.weddingStyle && (
                    <span className="text-xs text-muted-foreground">{prop.weddingStyle}</span>
                  )}
                  <StatusBadge status={prop.status} />
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {prop.weddingDate ? formatDate(prop.weddingDate) : 'data a definir'}
                  {prop.guestCount != null ? ` · ${prop.guestCount} convidados` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {prop.dateBusy && <Badge variant="danger">Data ocupada</Badge>}
                <div className="text-right">
                  <div className="text-sm font-medium">
                    {formatBrl(prop.totalCents - prop.discountCents)}
                  </div>
                  <div className="text-xs text-muted-foreground">{formatDate(prop.openedAt)}</div>
                </div>
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
        title="Nova proposta de casamento"
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
                Assessor (opcional)
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
                Estilo do casamento
              </label>
              <input
                value={openForm.weddingStyle}
                onChange={(e) => setOpenForm((f) => ({ ...f, weddingStyle: e.target.value }))}
                placeholder="clássico, rústico, praia, mini wedding…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Data do casamento
              </label>
              <input
                type="date"
                value={openForm.weddingDate}
                onChange={(e) => setOpenForm((f) => ({ ...f, weddingDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
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
              placeholder="O que o casal imagina para o casamento"
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

      {/* Modal: detalhe + orçamento + cronograma + checklist + status */}
      <Modal
        open={detailId !== null}
        onClose={() => {
          setDetailId(null)
          setStatusError(null)
          setItemError(null)
          setMarkError(null)
          setTaskError(null)
        }}
        title="Proposta de casamento"
        size="lg"
      >
        {detail.isPending || !p ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{p.customerName}</span>
              {p.weddingStyle && (
                <span className="text-xs text-muted-foreground">{p.weddingStyle}</span>
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
                  <dt className="text-xs text-muted-foreground">Data</dt>
                  <dd className={p.dateBusy ? 'font-medium text-red-600' : ''}>
                    {p.weddingDate ? formatDate(p.weddingDate) : '—'}
                    {p.dateBusy && ' · já há casamento nesta data'}
                  </dd>
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
                <span className="text-sm font-medium">
                  Total: {formatBrl(p.totalCents - p.discountCents)}
                  {p.discountCents > 0 && (
                    <span className="ml-2 text-xs font-normal text-muted-foreground">
                      ({formatBrl(p.totalCents)} − {formatBrl(p.discountCents)} de desconto)
                    </span>
                  )}
                </span>
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
                  {(catalog.data?.items ?? []).length > 0 && (
                    <div className="w-full">
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">
                        Do catálogo (autofill, opcional)
                      </label>
                      <select
                        value=""
                        onChange={(e) => {
                          const item = (catalog.data?.items ?? []).find(
                            (c) => c.id === e.target.value,
                          )
                          if (item) {
                            setItemForm((f) => ({
                              ...f,
                              description: item.name,
                              price: String(item.priceCents / 100),
                            }))
                          }
                        }}
                        className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                      >
                        <option value="">Preencher com um pacote/adicional do catálogo…</option>
                        {(catalog.data?.items ?? []).map((c) => (
                          <option key={c.id} value={c.id}>
                            [{c.kind === 'pacote' ? 'Pacote' : 'Adicional'}] {c.name} —{' '}
                            {formatBrl(c.priceCents)}
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
                      placeholder="Buffet, decoração, fotografia…"
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

              {/* Cupom (onda 1, backlog #10 — aplicado pelo painel; a IA não negocia preço) */}
              {p.couponCodeSnapshot ? (
                <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm">
                  <span>
                    Cupom <span className="font-mono font-medium">{p.couponCodeSnapshot}</span>{' '}
                    aplicado
                    <span className="ml-1 text-xs text-muted-foreground">
                      (−{formatBrl(p.discountCents)})
                    </span>
                  </span>
                  {!locked && (
                    <Button
                      variant="outline"
                      className="h-7 px-2 text-xs"
                      disabled={removeCouponMutation.isPending}
                      onClick={() => removeCouponMutation.mutate()}
                    >
                      Remover cupom
                    </Button>
                  )}
                </div>
              ) : (
                !locked && (
                  <form
                    className="flex flex-wrap items-end gap-2"
                    onSubmit={(e) => {
                      e.preventDefault()
                      applyCouponMutation.mutate()
                    }}
                  >
                    <div className="w-44">
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">
                        Cupom (opcional)
                      </label>
                      <input
                        value={couponCode}
                        maxLength={40}
                        placeholder="CÓDIGO"
                        onChange={(e) => setCouponCode(e.target.value)}
                        className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm uppercase"
                      />
                    </div>
                    <Button
                      type="submit"
                      variant="outline"
                      className="h-8 px-3 text-xs"
                      disabled={applyCouponMutation.isPending || !couponCode.trim()}
                    >
                      Aplicar
                    </Button>
                  </form>
                )
              )}
              {couponError && <p className="text-sm text-destructive">{couponError}</p>}
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
                      placeholder="Cerimônia, recepção, valsa, jantar…"
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
                    <div
                      key={tk.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="flex min-w-0 items-center gap-2">
                        <input
                          type="checkbox"
                          checked={tk.done}
                          disabled={locked || toggleTaskMutation.isPending}
                          onChange={() => {
                            setTaskError(null)
                            toggleTaskMutation.mutate(tk)
                          }}
                        />
                        <div className="min-w-0">
                          <span
                            className={`font-medium ${tk.done ? 'text-muted-foreground line-through' : ''}`}
                          >
                            {tk.title}
                          </span>
                          {tk.dueDate && (
                            <span className="ml-2 text-xs text-muted-foreground">
                              prazo {formatDate(tk.dueDate)}
                            </span>
                          )}
                          {tk.done && tk.doneAt && (
                            <span className="ml-2 text-xs text-emerald-600">
                              concluída {formatDate(tk.doneAt)}
                            </span>
                          )}
                          {tk.description && (
                            <p className="text-xs text-muted-foreground">{tk.description}</p>
                          )}
                        </div>
                      </div>
                      {!locked && (
                        <Button
                          variant="outline"
                          className="h-6 px-2 text-xs"
                          disabled={deleteTaskMutation.isPending}
                          onClick={() => deleteTaskMutation.mutate(tk.id)}
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
                    addTaskMutation.mutate()
                  }}
                >
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Tarefa
                    </label>
                    <input
                      value={taskForm.title}
                      onChange={(e) => setTaskForm((f) => ({ ...f, title: e.target.value }))}
                      required
                      maxLength={200}
                      placeholder="Provar bolo, definir trajes, prova do vestido…"
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-36">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Prazo (opcional)
                    </label>
                    <input
                      type="date"
                      value={taskForm.dueDate}
                      onChange={(e) => setTaskForm((f) => ({ ...f, dueDate: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={addTaskMutation.isPending}
                  >
                    Adicionar
                  </Button>
                </form>
              )}
              {taskError && <p className="text-sm text-destructive">{taskError}</p>}
            </div>

            {/* Plano de pagamento (onda 1, backlog #1 — manual até o gateway #50) */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Plano de pagamento</h3>
                <span className="text-xs text-muted-foreground">
                  sinal em aberto bloqueia o fechamento
                </span>
              </div>
              {(payments.data?.items ?? []).length === 0 ? (
                <p className="text-xs text-muted-foreground">
                  Nenhum sinal/parcela registrado ainda.
                </p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {(payments.data?.items ?? []).map((pay) => (
                    <div
                      key={pay.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="flex min-w-0 items-center gap-2">
                        <input
                          type="checkbox"
                          checked={pay.paid}
                          disabled={paymentsLocked || setPaidMutation.isPending}
                          onChange={() =>
                            setPaidMutation.mutate({ paymentId: pay.id, paid: !pay.paid })
                          }
                        />
                        <div className="min-w-0">
                          <span
                            className={`font-medium ${pay.paid ? 'text-muted-foreground line-through' : ''}`}
                          >
                            {pay.label || (pay.kind === 'sinal' ? 'Sinal' : 'Parcela')}
                          </span>
                          <span className="ml-2 text-xs text-muted-foreground">
                            vence {formatDate(pay.dueDate)}
                          </span>
                          {pay.kind === 'sinal' && (
                            <Badge variant={pay.paid ? 'success' : 'warning'}>
                              {pay.paid ? 'Sinal pago' : 'Sinal em aberto'}
                            </Badge>
                          )}
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <span className="tabular-nums">{formatBrl(pay.amountCents)}</span>
                        {!paymentsLocked && (
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            disabled={deletePaymentMutation.isPending}
                            onClick={() => deletePaymentMutation.mutate(pay.id)}
                          >
                            Remover
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {!paymentsLocked && (
                <form
                  className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    addPaymentMutation.mutate()
                  }}
                >
                  <div className="w-28">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Tipo
                    </label>
                    <select
                      value={paymentForm.kind}
                      onChange={(e) =>
                        setPaymentForm((f) => ({
                          ...f,
                          kind: e.target.value as 'sinal' | 'parcela',
                        }))
                      }
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    >
                      <option value="sinal">Sinal</option>
                      <option value="parcela">Parcela</option>
                    </select>
                  </div>
                  <div className="min-w-[7rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Rótulo (opcional)
                    </label>
                    <input
                      value={paymentForm.label}
                      maxLength={100}
                      placeholder="Parcela 2/6…"
                      onChange={(e) => setPaymentForm((f) => ({ ...f, label: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-36">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Vencimento
                    </label>
                    <input
                      type="date"
                      value={paymentForm.dueDate}
                      required
                      onChange={(e) => setPaymentForm((f) => ({ ...f, dueDate: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-24">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Valor (R$)
                    </label>
                    <input
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={paymentForm.amount}
                      required
                      onChange={(e) => setPaymentForm((f) => ({ ...f, amount: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={addPaymentMutation.isPending}
                  >
                    Adicionar
                  </Button>
                  <p className="w-full text-xs text-muted-foreground">
                    Pagamento é confirmado À MÃO pela equipe (Pix conferido) até o pagamento online
                    chegar. O lembrete automático avisa o casal 3 dias antes de cada vencimento
                    (configurável).
                  </p>
                </form>
              )}
              {paymentError && <p className="text-sm text-destructive">{paymentError}</p>}
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
