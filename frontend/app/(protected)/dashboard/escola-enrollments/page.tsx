'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { listClasses } from '@/lib/api/escola/classes'
import {
  createEnrollment,
  listEnrollments,
  updateEnrollmentStatus,
} from '@/lib/api/escola/enrollments'
import { deletePayment, listPayments, registerPayment } from '@/lib/api/escola/payments'
import { listStudents } from '@/lib/api/escola/students'
import {
  ALLOWED_NEXT,
  ESCOLA_ENROLLMENT_STATUSES,
  statusLabel,
  type EscolaEnrollmentStatusId,
} from '@/profiles/escola/escola-enrollment-status'
import {
  formatBrl,
  formatDate,
  formatMonth,
  shiftLabel,
  type ConflictDetail,
  type EscolaEnrollment,
} from '@/profiles/escola/escola-types'

function StatusBadge({ status }: { status: EscolaEnrollmentStatusId }) {
  const variant = status === 'ativa' ? 'success' : status === 'suspensa' ? 'warning' : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = { studentId: string; classId: string; notes: string }
const EMPTY: FormState = { studentId: '', classId: '', notes: '' }

type PayForm = { referenceMonth: string; amount: string; method: string; notes: string }
const EMPTY_PAY: PayForm = { referenceMonth: '', amount: '', method: '', notes: '' }

/**
 * Matrículas do EscolaBot (camada 8.19). Lista por status + filtro, criação manual (escolhe aluno +
 * turma; o backend valida vaga/anti-dupla e devolve 409 class_full/already_active), transições de
 * status (ativar/suspender/cancelar) e pagamento mensal (409 duplicate_payment). Detalhe com snapshots.
 */
export default function EscolaEnrollmentsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const [detail, setDetail] = useState<EscolaEnrollment | null>(null)
  const [statusTarget, setStatusTarget] = useState<EscolaEnrollmentStatusId | null>(null)
  const [payForm, setPayForm] = useState<PayForm>(EMPTY_PAY)
  const [payError, setPayError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['escola-enrollments', status, page],
    queryFn: () => listEnrollments({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const students = useQuery({
    queryKey: ['escola-students-all'],
    queryFn: () => listStudents({ active: true }),
  })
  const classes = useQuery({
    queryKey: ['escola-classes-all'],
    queryFn: () => listClasses({ onlyActive: true }),
  })

  const payments = useQuery({
    queryKey: ['escola-payments', detail?.id],
    queryFn: () => listPayments(detail!.id),
    enabled: detail !== null,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createEnrollment({
        studentId: form.studentId,
        classId: form.classId,
        notes: form.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['escola-enrollments'] })
      qc.invalidateQueries({ queryKey: ['escola-classes-all'] })
      qc.invalidateQueries({ queryKey: ['escola-classes'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'class_full') {
        const body = e.body as (ConflictDetail & { reason: string }) | null
        setFormError(`A turma "${body?.className ?? ''}" está lotada. Escolha outra.`)
      } else if (e instanceof ApiError && e.reason === 'already_active') {
        setFormError('Este aluno já tem uma matrícula ativa.')
      } else if (
        e instanceof ApiError &&
        (e.reason === 'class_inactive' || e.reason === 'student_inactive')
      ) {
        setFormError('Turma ou aluno inativo.')
      } else {
        setFormError('Erro ao criar a matrícula.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: EscolaEnrollmentStatusId) => {
      if (!detail) throw new Error('sem matrícula')
      return updateEnrollmentStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['escola-enrollments'] })
      qc.invalidateQueries({ queryKey: ['escola-classes-all'] })
      qc.invalidateQueries({ queryKey: ['escola-classes'] })
      setStatusTarget(null)
      setDetail(updated)
    },
  })

  const payMutation = useMutation({
    mutationFn: () => {
      if (!detail) throw new Error('sem matrícula')
      // referenceMonth: "YYYY-MM" do input month → "YYYY-MM-01".
      const ref = payForm.referenceMonth ? `${payForm.referenceMonth}-01` : ''
      return registerPayment(detail.id, {
        referenceMonth: ref,
        amountCents: Math.round(Number(payForm.amount || '0') * 100),
        method: payForm.method || null,
        notes: payForm.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['escola-payments', detail?.id] })
      setPayForm(EMPTY_PAY)
      setPayError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_payment') {
        setPayError('Já existe pagamento para esse mês.')
      } else if (e instanceof ApiError && e.reason === 'enrollment_cancelled') {
        setPayError('Não é possível registrar pagamento em matrícula cancelada.')
      } else {
        setPayError('Erro ao registrar pagamento.')
      }
    },
  })

  const delPayMutation = useMutation({
    mutationFn: (paymentId: string) => deletePayment(detail!.id, paymentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['escola-payments', detail?.id] }),
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Matrículas"
        description="Matrículas (assinatura mensal) da escola. A IA matricula pelo WhatsApp; você também pode criar manualmente."
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
        {ESCOLA_ENROLLMENT_STATUSES.map((s) => (
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
                  {m.className} · {m.classGrade} · {shiftLabel(m.classShift)} · desde{' '}
                  {formatDate(m.startDate)}
                </p>
              </div>
              <span className="text-xs text-muted-foreground">
                {formatBrl(m.classMonthlyCents)}/mês
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
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Aluno</label>
            <select
              value={form.studentId}
              onChange={(e) => setForm((f) => ({ ...f, studentId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(students.data?.items ?? []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                  {s.intendedGrade ? ` (${s.intendedGrade})` : ''}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Turma</label>
            <select
              value={form.classId}
              onChange={(e) => setForm((f) => ({ ...f, classId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(classes.data?.items ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} · {c.grade} · {shiftLabel(c.shift)} ({formatBrl(c.monthlyCents)}/mês)
                </option>
              ))}
            </select>
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
            <Button
              type="submit"
              disabled={createMutation.isPending || !form.studentId || !form.classId}
            >
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
                  <dt className="text-xs text-muted-foreground">Turma</dt>
                  <dd>
                    {detail.className} ({formatBrl(detail.classMonthlyCents)}/mês)
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Série / turno</dt>
                  <dd>
                    {detail.classGrade} · {shiftLabel(detail.classShift)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Responsável</dt>
                  <dd>{detail.responsibleName ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Desde</dt>
                  <dd>{formatDate(detail.startDate)}</dd>
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
                {detail.notes && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Observações</dt>
                    <dd>{detail.notes}</dd>
                  </div>
                )}
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
                            {formatMonth(p.referenceMonth)} · {formatBrl(p.amountCents)}
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
        description="O responsável será notificado em retomada (ativa) e cancelamento, se houver vínculo com o WhatsApp. Suspensa mantém a vaga ocupada."
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
