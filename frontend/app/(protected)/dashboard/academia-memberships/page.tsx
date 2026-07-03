'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { listClasses } from '@/lib/api/academia/classes'
import {
  createMembership,
  listMemberships,
  updateMembershipStatus,
} from '@/lib/api/academia/memberships'
import { deletePayment, listPayments, recordPayment } from '@/lib/api/academia/payments'
import { listPlans } from '@/lib/api/academia/plans'
import { ApiError } from '@/lib/api/client'
import {
  ACADEMIA_MEMBERSHIP_STATUSES,
  ALLOWED_NEXT,
  statusLabel,
  type AcademiaMembershipStatusId,
} from '@/profiles/academia/academia-membership-status'
import {
  dayOfWeekLabel,
  formatDate,
  formatMonth,
  formatPrice,
  formatTime,
  type ConflictDetail,
  type Membership,
} from '@/profiles/academia/academia-types'

function StatusBadge({ status }: { status: AcademiaMembershipStatusId }) {
  const variant = status === 'ativa' ? 'success' : status === 'suspensa' ? 'warning' : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  planId: string
  classIds: string[]
  studentName: string
  studentPhone: string
  notes: string
}
const EMPTY: FormState = { planId: '', classIds: [], studentName: '', studentPhone: '', notes: '' }

type PayForm = { referenceMonth: string; amount: string; method: string; notes: string }
const EMPTY_PAY: PayForm = { referenceMonth: '', amount: '', method: '', notes: '' }

/**
 * Matrículas do AcademiaBot (camada 7.7). Lista por status + filtros, Modal multi-select de aulas
 * (com vagas inline, desabilita lotadas), detalhe com aba de pagamentos. Trata 409 class_full +
 * already_active.
 */
export default function AcademiaMembershipsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const [detail, setDetail] = useState<Membership | null>(null)
  const [statusTarget, setStatusTarget] = useState<AcademiaMembershipStatusId | null>(null)
  const [payForm, setPayForm] = useState<PayForm>(EMPTY_PAY)
  const [payError, setPayError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['academia-memberships', status, page],
    queryFn: () => listMemberships({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const plans = useQuery({
    queryKey: ['academia-plans-all'],
    queryFn: () => listPlans({ onlyActive: true }),
  })
  const classes = useQuery({
    queryKey: ['academia-classes-all'],
    queryFn: () => listClasses({ onlyActive: true }),
  })

  const payments = useQuery({
    queryKey: ['academia-payments', detail?.id],
    queryFn: () => listPayments(detail!.id),
    enabled: detail !== null,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createMembership({
        planId: form.planId,
        classIds: form.classIds,
        studentName: form.studentName,
        studentPhone: form.studentPhone || null,
        notes: form.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-memberships'] })
      qc.invalidateQueries({ queryKey: ['academia-classes-all'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'class_full') {
        const body = e.body as (ConflictDetail & { reason: string }) | null
        setFormError(`A aula "${body?.className ?? ''}" está lotada. Escolha outra.`)
      } else if (e instanceof ApiError && e.reason === 'already_active') {
        setFormError('Este cliente já tem uma matrícula ativa.')
      } else if (e instanceof ApiError && e.reason === 'no_classes') {
        setFormError('Escolha ao menos uma aula.')
      } else if (
        e instanceof ApiError &&
        (e.reason === 'plan_inactive' || e.reason === 'class_inactive')
      ) {
        setFormError('Plano ou aula inativo.')
      } else {
        setFormError('Erro ao criar a matrícula.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: AcademiaMembershipStatusId) => {
      if (!detail) throw new Error('sem matrícula')
      return updateMembershipStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['academia-memberships'] })
      qc.invalidateQueries({ queryKey: ['academia-classes-all'] })
      setStatusTarget(null)
      setDetail(updated)
    },
  })

  const payMutation = useMutation({
    mutationFn: () => {
      if (!detail) throw new Error('sem matrícula')
      // referenceMonth: "YYYY-MM" do input month → "YYYY-MM-01".
      const ref = payForm.referenceMonth ? `${payForm.referenceMonth}-01` : ''
      return recordPayment(detail.id, {
        referenceMonth: ref,
        amountCents: Math.round(Number(payForm.amount || '0') * 100),
        method: payForm.method || null,
        notes: payForm.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-payments', detail?.id] })
      setPayForm(EMPTY_PAY)
      setPayError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_payment') {
        setPayError('Já existe pagamento para esse mês.')
      } else if (e instanceof ApiError && e.reason === 'membership_not_active') {
        setPayError('Só é possível registrar pagamento em matrícula ativa.')
      } else {
        setPayError('Erro ao registrar pagamento.')
      }
    },
  })

  const delPayMutation = useMutation({
    mutationFn: (paymentId: string) => deletePayment(detail!.id, paymentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-payments', detail?.id] }),
  })

  function toggleClassId(id: string) {
    setForm((f) =>
      f.classIds.includes(id)
        ? { ...f, classIds: f.classIds.filter((c) => c !== id) }
        : { ...f, classIds: [...f.classIds, id] },
    )
  }

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Matrículas"
        description="Matrículas (assinaturas) da academia. A IA matricula pelo WhatsApp; você também pode criar manualmente."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setModalOpen(true)
            }}
          >
            Nova matrícula
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
        {ACADEMIA_MEMBERSHIP_STATUSES.map((s) => (
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
        <p className="text-sm text-destructive">Erro ao carregar as matrículas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma matrícula encontrada.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((m) => (
            <button
              key={m.id}
              onClick={() => setDetail(m)}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{m.studentName}</span>
                  <StatusBadge status={m.status} />
                </div>
                <p className="text-xs text-muted-foreground">
                  {m.planName} · {m.classes.length} aula(s) · desde {formatDate(m.startDate)}
                </p>
              </div>
              <span className="text-xs text-muted-foreground">
                {formatPrice(m.planMonthlyCents)}/mês
              </span>
            </button>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} matrícula(s)
          </span>
          <div className="flex gap-1">
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ←
            </Button>
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              →
            </Button>
          </div>
        </div>
      )}

      {/* Modal: nova matrícula */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova matrícula" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Plano</label>
            <select
              value={form.planId}
              onChange={(e) => setForm((f) => ({ ...f, planId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(plans.data?.items ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({formatPrice(p.monthlyCents)}/mês)
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Aulas</label>
            <div className="max-h-48 space-y-1 overflow-y-auto rounded-md border border-border p-2">
              {(classes.data?.items ?? []).map((c) => {
                const full = c.remainingSlots <= 0 && !form.classIds.includes(c.id)
                return (
                  <label
                    key={c.id}
                    className={`flex items-center gap-2 rounded px-2 py-1 text-sm ${full ? 'opacity-50' : 'hover:bg-muted/40'}`}
                  >
                    <input
                      type="checkbox"
                      disabled={full}
                      checked={form.classIds.includes(c.id)}
                      onChange={() => toggleClassId(c.id)}
                    />
                    <span>
                      {dayOfWeekLabel(c.dayOfWeek)} {formatTime(c.startTime)} · {c.modality} &quot;
                      {c.name}&quot;
                    </span>
                    <span className="ml-auto text-xs text-muted-foreground">
                      {c.remainingSlots} vaga(s)
                    </span>
                  </label>
                )
              })}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Aluno</label>
              <input
                value={form.studentName}
                onChange={(e) => setForm((f) => ({ ...f, studentName: e.target.value }))}
                required
                maxLength={200}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone (opcional)
              </label>
              <input
                value={form.studentPhone}
                onChange={(e) => setForm((f) => ({ ...f, studentPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações
            </label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending || form.classIds.length === 0}>
              {createMutation.isPending ? 'Criando…' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + status + pagamentos */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Matrícula" size="lg">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.studentName}</span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Plano</dt>
                  <dd>
                    {detail.planName} ({formatPrice(detail.planMonthlyCents)}/mês)
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Desde</dt>
                  <dd>{formatDate(detail.startDate)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{detail.studentPhone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Origem</dt>
                  <dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd>
                </div>
                {detail.endDate && (
                  <div>
                    <dt className="text-xs text-muted-foreground">Encerrada em</dt>
                    <dd>{formatDate(detail.endDate)}</dd>
                  </div>
                )}
                <div className="col-span-2">
                  <dt className="text-xs text-muted-foreground">Aulas</dt>
                  <dd>
                    {detail.classes
                      .map(
                        (c) =>
                          `${dayOfWeekLabel(c.dayOfWeek)} ${formatTime(c.startTime)} ${c.className}`,
                      )
                      .join(' · ') || '—'}
                  </dd>
                </div>
              </dl>
            </Card>

            {ALLOWED_NEXT[detail.status].length > 0 && (
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Mudar status para…
                </label>
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[detail.status].map((next) => (
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
            )}

            {/* Aba pagamentos */}
            <Card>
              <Section title="Pagamentos">
                <span />
              </Section>
              {payments.isPending ? (
                <p className="text-sm text-muted-foreground">Carregando…</p>
              ) : (
                <div className="space-y-3">
                  <p className="text-xs text-muted-foreground">
                    Último mês pago: {formatMonth(payments.data?.summary.lastPaidMonth ?? null)} ·{' '}
                    Meses em aberto: {payments.data?.summary.monthsOpen ?? 0}
                  </p>
                  {(payments.data?.items.length ?? 0) > 0 && (
                    <ul className="space-y-1 text-sm">
                      {payments.data!.items.map((p) => (
                        <li key={p.id} className="flex items-center justify-between gap-2">
                          <span>
                            {formatMonth(p.referenceMonth)} · {formatPrice(p.amountCents)}
                            {p.method ? ` · ${p.method}` : ''}
                          </span>
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            onClick={() => delPayMutation.mutate(p.id)}
                          >
                            Excluir
                          </Button>
                        </li>
                      ))}
                    </ul>
                  )}
                  {detail.status === 'ativa' && (
                    <form
                      className="grid grid-cols-2 gap-2"
                      onSubmit={(e) => {
                        e.preventDefault()
                        payMutation.mutate()
                      }}
                    >
                      <input
                        type="month"
                        value={payForm.referenceMonth}
                        required
                        onChange={(e) =>
                          setPayForm((f) => ({ ...f, referenceMonth: e.target.value }))
                        }
                        className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                      />
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="Valor R$"
                        value={payForm.amount}
                        required
                        onChange={(e) => setPayForm((f) => ({ ...f, amount: e.target.value }))}
                        className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                      />
                      <input
                        placeholder="Forma (Pix, dinheiro…)"
                        value={payForm.method}
                        onChange={(e) => setPayForm((f) => ({ ...f, method: e.target.value }))}
                        className="col-span-2 rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                      />
                      {payError && (
                        <p className="col-span-2 text-xs text-destructive">{payError}</p>
                      )}
                      <div className="col-span-2 flex justify-end">
                        <Button
                          type="submit"
                          className="h-7 px-3 text-xs"
                          disabled={payMutation.isPending}
                        >
                          {payMutation.isPending ? 'Registrando…' : 'Registrar pagamento'}
                        </Button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </Card>
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O aluno será notificado em retomada (ativa) e cancelamento, se houver vínculo com o WhatsApp. Suspensa mantém a vaga ocupada."
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
