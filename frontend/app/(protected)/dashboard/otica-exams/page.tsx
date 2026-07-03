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
import { createExam, listExams, updateExamStatus } from '@/lib/api/otica/exams'
import { listProfessionals } from '@/lib/api/otica/professionals'
import {
  ALLOWED_NEXT,
  OTICA_EXAM_STATUSES,
  statusLabel,
  type OticaExamStatusId,
} from '@/profiles/otica/otica-exam-status'
import {
  formatDate,
  formatTime,
  type ConflictDetail,
  type ExamAppointment,
} from '@/profiles/otica/otica-types'

function StatusBadge({ status }: { status: OticaExamStatusId }) {
  const variant =
    status === 'confirmado'
      ? 'success'
      : status === 'realizado'
        ? 'info'
        : status === 'agendado'
          ? 'warning'
          : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  professionalId: string
  customerName: string
  date: string
  time: string
  notes: string
}
const EMPTY: FormState = { professionalId: '', customerName: '', date: '', time: '', notes: '' }

function groupByDay(items: ExamAppointment[]): { day: string; items: ExamAppointment[] }[] {
  const groups: { day: string; items: ExamAppointment[] }[] = []
  for (const a of items) {
    const day = formatDate(a.startAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(a)
    else groups.push({ day, items: [a] })
  }
  return groups
}

/**
 * Agenda de exames de vista do OticaBot (camada 8.12, FLUXO A). Lista por dia com filtro de
 * status/profissional, criação manual via Modal (nome do cliente é texto livre — ótica NÃO tem
 * paciente como entidade; a duração vem da config), trata 409 conflict_slot por profissional + 400
 * (outside_hours, inactive_professional), e detalhe com mudança de status (notifica o cliente em
 * confirmado/cancelado, se houver vínculo com o WhatsApp).
 */
export default function OticaExamsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [professionalFilter, setProfessionalFilter] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState<ConflictDetail | null>(null)

  const [detail, setDetail] = useState<ExamAppointment | null>(null)
  const [statusTarget, setStatusTarget] = useState<OticaExamStatusId | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['otica-exams', status, professionalFilter, page],
    queryFn: () =>
      listExams({
        status: status || undefined,
        professionalId: professionalFilter || undefined,
        page,
        pageSize: 50,
      }),
    placeholderData: keepPreviousData,
  })

  const professionals = useQuery({
    queryKey: ['otica-exam-professionals'],
    queryFn: () => listProfessionals({ onlyActive: true }),
  })

  const createMutation = useMutation({
    mutationFn: () => {
      const startAt = new Date(`${form.date}T${form.time}:00`).toISOString()
      return createExam({
        professionalId: form.professionalId,
        customerName: form.customerName,
        startAt,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['otica-exams'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
      setConflict(null)
    },
    onError: (e) => {
      setConflict(null)
      if (e instanceof ApiError && e.reason === 'conflict_slot') {
        setFormError('Esse profissional já tem exame nesse horário.')
        const body = e.body as { conflict?: ConflictDetail } | null
        if (body?.conflict) setConflict(body.conflict)
      } else if (e instanceof ApiError && e.reason === 'outside_hours') {
        setFormError('Esse horário está fora do funcionamento da ótica.')
      } else if (e instanceof ApiError && e.reason === 'inactive_professional') {
        setFormError('Esse profissional está inativo.')
      } else {
        setFormError('Erro ao criar o exame.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: OticaExamStatusId) => {
      if (!detail) throw new Error('sem exame')
      return updateExamStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['otica-exams'] })
      setStatusTarget(null)
      setDetail(updated)
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))
  const groups = groupByDay(items)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Exames"
        description="Exames de vista. A IA agenda pelo WhatsApp; você também pode criar manualmente."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setConflict(null)
              setModalOpen(true)
            }}
          >
            Novo exame
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
          Todos
        </button>
        {OTICA_EXAM_STATUSES.map((s) => (
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
        <select
          value={professionalFilter}
          onChange={(e) => {
            setProfessionalFilter(e.target.value)
            setPage(0)
          }}
          className="ml-auto rounded-md border border-border bg-background px-3 py-1.5 text-xs"
        >
          <option value="">Todos os profissionais</option>
          {(professionals.data?.items ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a agenda.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum exame encontrado.</p>
      ) : (
        <div className="space-y-6">
          {groups.map((g) => (
            <section key={g.day} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.day}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((a) => (
                  <button
                    key={a.id}
                    onClick={() => setDetail(a)}
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{a.customerName}</span>
                        <StatusBadge status={a.status} />
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {a.professionalName} · {formatTime(a.startAt)}–{formatTime(a.endAt)}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">{formatTime(a.startAt)}</span>
                  </button>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} exame(s)
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

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo exame" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
            <input
              value={form.customerName}
              onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))}
              required
              maxLength={200}
              placeholder="Nome do cliente"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Profissional
            </label>
            <select
              value={form.professionalId}
              onChange={(e) => setForm((f) => ({ ...f, professionalId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(professionals.data?.items ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data</label>
              <input
                type="date"
                value={form.date}
                onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Hora</label>
              <input
                type="time"
                value={form.time}
                onChange={(e) => setForm((f) => ({ ...f, time: e.target.value }))}
                required
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
          {conflict && (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">
              Ocupado por <strong>{conflict.customerName}</strong>, das{' '}
              {formatTime(conflict.startAt)} às {formatTime(conflict.endAt)}.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Criando…' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Exame" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.customerName}</span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Profissional</dt>
                  <dd>{detail.professionalName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Origem</dt>
                  <dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Data</dt>
                  <dd>{formatDate(detail.startAt)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Horário</dt>
                  <dd>
                    {formatTime(detail.startAt)}–{formatTime(detail.endAt)}
                  </dd>
                </div>
                {detail.notes && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Observações</dt>
                    <dd>{detail.notes}</dd>
                  </div>
                )}
              </dl>
            </Card>

            {ALLOWED_NEXT[detail.status].length > 0 ? (
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
            ) : (
              <p className="text-xs text-muted-foreground">Este exame está num status final.</p>
            )}
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O cliente será notificado automaticamente em confirmação e cancelamento, se houver vínculo com o WhatsApp."
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
