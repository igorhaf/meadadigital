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
import {
  createSession,
  listSessions,
  updateSession,
  updateSessionStatus,
} from '@/lib/api/fotografia/appointments'
import { listPackages } from '@/lib/api/fotografia/packages'
import { listProfessionals } from '@/lib/api/fotografia/professionals'
import { useOnSync } from '@/lib/use-synced-form'
import {
  ALLOWED_NEXT,
  FOTOGRAFIA_APPOINTMENT_STATUSES,
  statusLabel,
  type FotografiaAppointmentStatusId,
} from '@/profiles/fotografia/fotografia-appointment-status'
import {
  formatDate,
  formatTime,
  type ConflictDetail,
  type FotografiaSession,
} from '@/profiles/fotografia/fotografia-types'

function StatusBadge({ status }: { status: FotografiaAppointmentStatusId }) {
  const variant =
    status === 'confirmada'
      ? 'success'
      : status === 'entregue'
        ? 'success'
        : status === 'realizada'
          ? 'info'
          : status === 'agendada'
            ? 'warning'
            : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  professionalId: string
  packageId: string
  customerName: string
  customerPhone: string
  date: string
  time: string
  notes: string
}
const EMPTY: FormState = {
  professionalId: '',
  packageId: '',
  customerName: '',
  customerPhone: '',
  date: '',
  time: '',
  notes: '',
}

function groupByDay(items: FotografiaSession[]): { day: string; items: FotografiaSession[] }[] {
  const groups: { day: string; items: FotografiaSession[] }[] = []
  for (const a of items) {
    const day = formatDate(a.startAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(a)
    else groups.push({ day, items: [a] })
  }
  return groups
}

/**
 * Agenda do FotografiaBot (camada 8.16). Lista as sessões por dia com filtro de status/profissional,
 * criação manual via Modal (cliente é o contato direto — nome/telefone digitados; pacote → duração),
 * trata 409 conflict_slot por profissional + 400 (outside_hours, inactive_*), e detalhe com mudança
 * de status (agendada→confirmada→realizada→entregue) + edição do MATERIAL (link de entrega + obs)
 * via PATCH /api/fotografia/sessions/{id}. Mostra o prazo de entrega (delivery_due_date).
 */
export default function FotografiaAppointmentsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [professionalFilter, setProfessionalFilter] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState<ConflictDetail | null>(null)

  const [detail, setDetail] = useState<FotografiaSession | null>(null)
  const [statusTarget, setStatusTarget] = useState<FotografiaAppointmentStatusId | null>(null)

  // Editor do material (link de entrega + observações).
  const [materialLink, setMaterialLink] = useState('')
  const [materialNotes, setMaterialNotes] = useState('')
  const [materialError, setMaterialError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['fotografia-sessions', status, professionalFilter, page],
    queryFn: () =>
      listSessions({
        status: status || undefined,
        professionalId: professionalFilter || undefined,
        page,
        pageSize: 50,
      }),
    placeholderData: keepPreviousData,
  })

  const professionals = useQuery({
    queryKey: ['fotografia-appt-professionals'],
    queryFn: () => listProfessionals({ onlyActive: true }),
  })
  const packages = useQuery({
    queryKey: ['fotografia-appt-packages'],
    queryFn: () => listPackages({ onlyActive: true }),
  })

  useOnSync(detail, (d) => {
    setMaterialLink(d.deliveryLink ?? '')
    setMaterialNotes(d.notes ?? '')
    setMaterialError(null)
  })

  const createMutation = useMutation({
    mutationFn: () => {
      const startAt = new Date(`${form.date}T${form.time}:00`).toISOString()
      return createSession({
        professionalId: form.professionalId,
        packageId: form.packageId,
        customerName: form.customerName,
        customerPhone: form.customerPhone || null,
        startAt,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fotografia-sessions'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
      setConflict(null)
    },
    onError: (e) => {
      setConflict(null)
      if (e instanceof ApiError && e.reason === 'conflict_slot') {
        setFormError('Esse profissional já tem sessão nesse horário.')
        const body = e.body as { conflict?: ConflictDetail } | null
        if (body?.conflict) setConflict(body.conflict)
      } else if (e instanceof ApiError && e.reason === 'outside_hours') {
        setFormError('Esse horário está fora do funcionamento do estúdio.')
      } else if (e instanceof ApiError && e.reason === 'inactive_professional') {
        setFormError('Esse profissional está inativo.')
      } else if (e instanceof ApiError && e.reason === 'inactive_package') {
        setFormError('Esse pacote está inativo.')
      } else {
        setFormError('Erro ao criar a sessão.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: FotografiaAppointmentStatusId) => {
      if (!detail) throw new Error('sem sessão')
      return updateSessionStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['fotografia-sessions'] })
      setStatusTarget(null)
      setDetail(updated)
    },
  })

  const materialMutation = useMutation({
    mutationFn: () => {
      if (!detail) throw new Error('sem sessão')
      return updateSession(detail.id, {
        deliveryLink: materialLink.trim() || null,
        notes: materialNotes.trim() || null,
      })
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['fotografia-sessions'] })
      setDetail(updated)
      setMaterialError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_link') {
        setMaterialError('O link parece inválido.')
      } else {
        setMaterialError('Erro ao salvar o material.')
      }
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))
  const groups = groupByDay(items)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Agenda"
        description="Sessões de fotografia. A IA agenda pelo WhatsApp; você também pode criar manualmente e entregar o material."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setConflict(null)
              setModalOpen(true)
            }}
          >
            Nova sessão
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
        {FOTOGRAFIA_APPOINTMENT_STATUSES.map((s) => (
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
        <p className="text-sm text-muted-foreground">Nenhuma sessão encontrada.</p>
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
                        <Badge variant="info">{a.packageName}</Badge>
                        <StatusBadge status={a.status} />
                        {a.deliveryLink && <Badge variant="success">material entregue</Badge>}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {a.professionalName} · {formatTime(a.startAt)}–{formatTime(a.endAt)}
                        {a.deliveryDueDate ? ` · entrega até ${formatDate(a.deliveryDueDate)}` : ''}
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
            Página {page + 1} de {totalPages} · {total} sessão(ões)
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

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova sessão" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cliente
              </label>
              <input
                value={form.customerName}
                onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))}
                required
                maxLength={200}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone
              </label>
              <input
                value={form.customerPhone}
                onChange={(e) => setForm((f) => ({ ...f, customerPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
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
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Pacote</label>
              <select
                value={form.packageId}
                onChange={(e) => setForm((f) => ({ ...f, packageId: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {(packages.data?.items ?? []).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} ({t.durationMinutes} min)
                  </option>
                ))}
              </select>
            </div>
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

      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Sessão" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.customerName}</span>
              <Badge variant="info">{detail.packageName}</Badge>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Profissional</dt>
                  <dd>{detail.professionalName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Pacote</dt>
                  <dd>{detail.packageName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{detail.customerPhone ?? '—'}</dd>
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
                <div>
                  <dt className="text-xs text-muted-foreground">Prazo de entrega</dt>
                  <dd>{detail.deliveryDueDate ? formatDate(detail.deliveryDueDate) : '—'}</dd>
                </div>
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
              <p className="text-xs text-muted-foreground">Esta sessão está num status final.</p>
            )}

            <div className="space-y-3 rounded-md border border-border p-3">
              <h3 className="text-sm font-medium">Material entregue</h3>
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Link do material (galeria, drive…)
                </label>
                <input
                  value={materialLink}
                  onChange={(e) => setMaterialLink(e.target.value)}
                  placeholder="https://…"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Observações
                </label>
                <textarea
                  value={materialNotes}
                  onChange={(e) => setMaterialNotes(e.target.value)}
                  rows={2}
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                />
              </div>
              {materialError && <p className="text-sm text-destructive">{materialError}</p>}
              <div className="flex justify-end">
                <Button
                  className="h-8 px-3 text-xs"
                  disabled={materialMutation.isPending}
                  onClick={() => materialMutation.mutate()}
                >
                  {materialMutation.isPending ? 'Salvando…' : 'Salvar material'}
                </Button>
              </div>
            </div>
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
