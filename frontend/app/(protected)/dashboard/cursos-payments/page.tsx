'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { listEnrollments } from '@/lib/api/cursos/enrollments'
import { deletePayment, listPayments, recordPayment } from '@/lib/api/cursos/payments'
import {
  statusLabel,
  type CursoEnrollmentStatusId,
} from '@/profiles/cursos/curso-enrollment-status'
import { formatBrl, formatMonth, type Enrollment } from '@/profiles/cursos/cursos-types'

function StatusBadge({ status }: { status: CursoEnrollmentStatusId }) {
  const variant =
    status === 'ativa'
      ? 'success'
      : status === 'trancada'
        ? 'warning'
        : status === 'concluida'
          ? 'default'
          : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type PayForm = { referenceMonth: string; amount: string; method: string; notes: string }
const EMPTY_PAY: PayForm = { referenceMonth: '', amount: '', method: '', notes: '' }

/**
 * Pagamentos manuais do CursosBot (camada 8.20). Lista as matrículas ativas; ao selecionar uma,
 * mostra/registra os pagamentos mensais (espelho da aba de pagamentos da matrícula). SEM cobrança
 * automática.
 */
export default function CursosPaymentsPage() {
  const qc = useQueryClient()
  const [selected, setSelected] = useState<Enrollment | null>(null)
  const [payForm, setPayForm] = useState<PayForm>(EMPTY_PAY)
  const [payError, setPayError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['cursos-enrollments-pay', 'ativa'],
    queryFn: () => listEnrollments({ status: 'ativa', pageSize: 100 }),
  })

  const payments = useQuery({
    queryKey: ['cursos-payments', selected?.id],
    queryFn: () => listPayments(selected!.id),
    enabled: selected !== null,
  })

  const payMutation = useMutation({
    mutationFn: () => {
      if (!selected) throw new Error('sem matrícula')
      const ref = payForm.referenceMonth ? `${payForm.referenceMonth}-01` : ''
      return recordPayment(selected.id, {
        referenceMonth: ref,
        amountCents: Math.round(Number(payForm.amount || '0') * 100),
        method: payForm.method || null,
        notes: payForm.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cursos-payments', selected?.id] })
      setPayForm(EMPTY_PAY)
      setPayError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_payment') {
        setPayError('Já existe pagamento para esse mês.')
      } else if (e instanceof ApiError && e.reason === 'enrollment_not_active') {
        setPayError('Só é possível registrar pagamento em matrícula ativa.')
      } else {
        setPayError('Erro ao registrar pagamento.')
      }
    },
  })

  const delPayMutation = useMutation({
    mutationFn: (paymentId: string) => deletePayment(selected!.id, paymentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cursos-payments', selected?.id] }),
  })

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pagamentos"
        description="Pagamentos mensais manuais das matrículas ativas. Sem cobrança automática."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as matrículas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma matrícula ativa.</p>
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div className="divide-y divide-border rounded-lg border border-border">
            {items.map((m) => (
              <button
                key={m.id}
                onClick={() => {
                  setSelected(m)
                  setPayForm(EMPTY_PAY)
                  setPayError(null)
                }}
                className={`flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40 ${selected?.id === m.id ? 'bg-muted/40' : ''}`}
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{m.studentName}</span>
                    <StatusBadge status={m.status} />
                  </div>
                  <p className="text-xs text-muted-foreground">{m.courseTitle}</p>
                </div>
                <span className="text-xs text-muted-foreground">
                  {formatBrl(m.courseMonthlyCents)}/mês
                </span>
              </button>
            ))}
          </div>

          <Card>
            {!selected ? (
              <p className="text-sm text-muted-foreground">
                Selecione uma matrícula para ver os pagamentos.
              </p>
            ) : (
              <div className="space-y-3">
                <Section title={`Pagamentos — ${selected.studentName}`}>
                  <span />
                </Section>
                {payments.isPending ? (
                  <p className="text-sm text-muted-foreground">Carregando…</p>
                ) : (
                  <>
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
                  </>
                )}
              </div>
            )}
          </Card>
        </div>
      )}
    </div>
  )
}
