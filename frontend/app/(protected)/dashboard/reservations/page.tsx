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
  createReservation,
  listReservations,
  updateReservationStatus,
} from '@/lib/api/restaurant/reservations'
import { listTables } from '@/lib/api/restaurant/tables'
import {
  ALLOWED_NEXT,
  RESERVATION_STATUSES,
  statusLabel,
  type ReservationStatusId,
} from '@/profiles/restaurant/reservation-status'
import {
  formatDate,
  formatTime,
  type ConflictDetail,
  type Reservation,
} from '@/profiles/restaurant/restaurant-types'

function StatusBadge({ status }: { status: ReservationStatusId }) {
  const variant =
    status === 'confirmada'
      ? 'success'
      : status === 'realizada'
        ? 'info'
        : status === 'pendente'
          ? 'warning'
          : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  tableId: string
  guestName: string
  guestPhone: string
  date: string // YYYY-MM-DD
  time: string // HH:MM
  numPeople: string
  notes: string
}

const EMPTY: FormState = {
  tableId: '',
  guestName: '',
  guestPhone: '',
  date: '',
  time: '',
  numPeople: '2',
  notes: '',
}

/** Agrupa reservas por dia (DD/MM/AAAA) preservando a ordem (a API já devolve por start_at asc). */
function groupByDay(items: Reservation[]): { day: string; items: Reservation[] }[] {
  const groups: { day: string; items: Reservation[] }[] = []
  for (const r of items) {
    const day = formatDate(r.startAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(r)
    else groups.push({ day, items: [r] })
  }
  return groups
}

/**
 * Reservas do MesaBot (camada 7.3). Lista agrupada por dia com filtro de status, criação manual via
 * Modal (trata 409 conflict_slot mostrando quem ocupa o slot) e detalhe com mudança de status
 * (notifica o cliente, se vinculado ao WhatsApp).
 */
export default function ReservationsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  // Modal de criação.
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState<ConflictDetail | null>(null)

  // Modal de detalhe + transição de status.
  const [detail, setDetail] = useState<Reservation | null>(null)
  const [statusTarget, setStatusTarget] = useState<ReservationStatusId | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['restaurant-reservations', status, page],
    queryFn: () => listReservations({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const tables = useQuery({
    queryKey: ['restaurant-tables-all'],
    queryFn: () => listTables({ onlyAvailable: true }),
  })

  const createMutation = useMutation({
    mutationFn: () => {
      // Combina date+time no fuso LOCAL do browser → ISO instant (o backend reinterpreta em BRT
      // para validar a janela; em dev BR os dois batem).
      const startAt = new Date(`${form.date}T${form.time}:00`).toISOString()
      return createReservation({
        tableId: form.tableId,
        guestName: form.guestName,
        guestPhone: form.guestPhone || null,
        startAt,
        numPeople: Math.round(Number(form.numPeople)),
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['restaurant-reservations'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
      setConflict(null)
    },
    onError: (e) => {
      setConflict(null)
      if (e instanceof ApiError && e.reason === 'conflict_slot') {
        setFormError('Esse horário já está ocupado nessa mesa.')
        const body = e.body as { conflict?: ConflictDetail } | null
        if (body?.conflict) setConflict(body.conflict)
      } else if (e instanceof ApiError && e.reason === 'outside_hours') {
        setFormError('Esse horário está fora do funcionamento do restaurante.')
      } else if (e instanceof ApiError && e.reason === 'table_not_found') {
        setFormError('Mesa inválida.')
      } else {
        setFormError('Erro ao criar a reserva.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: ReservationStatusId) => {
      if (!detail) throw new Error('sem reserva')
      return updateReservationStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['restaurant-reservations'] })
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
        title="Reservas"
        description="Agenda de reservas do restaurante. A IA cria reservas pelo WhatsApp; você também pode criar manualmente."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setConflict(null)
              setModalOpen(true)
            }}
          >
            Nova reserva
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
        {RESERVATION_STATUSES.map((s) => (
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
        <p className="text-sm text-destructive">Erro ao carregar as reservas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma reserva encontrada.</p>
      ) : (
        <div className="space-y-6">
          {groups.map((g) => (
            <section key={g.day} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.day}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((r) => (
                  <button
                    key={r.id}
                    onClick={() => setDetail(r)}
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{r.guestName}</span>
                        <StatusBadge status={r.status} />
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {r.tableLabel} · {r.numPeople} pessoa(s) · {formatTime(r.startAt)}–
                        {formatTime(r.endAt)}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">{formatTime(r.startAt)}</span>
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
            Página {page + 1} de {totalPages} · {total} reserva(s)
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

      {/* ---- Modal: nova reserva ---- */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova reserva" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Mesa</label>
            <select
              value={form.tableId}
              onChange={(e) => setForm((f) => ({ ...f, tableId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(tables.data?.items ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.label} (cap. {t.capacity})
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
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Nº de pessoas
              </label>
              <input
                type="number"
                min="1"
                max="50"
                value={form.numPeople}
                required
                onChange={(e) => setForm((f) => ({ ...f, numPeople: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone (opcional)
              </label>
              <input
                value={form.guestPhone}
                onChange={(e) => setForm((f) => ({ ...f, guestPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Nome do cliente
            </label>
            <input
              value={form.guestName}
              onChange={(e) => setForm((f) => ({ ...f, guestName: e.target.value }))}
              required
              maxLength={120}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações (opcional)
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
              Ocupado por <strong>{conflict.guestName}</strong>, das {formatTime(conflict.startAt)}{' '}
              às {formatTime(conflict.endAt)}.
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

      {/* ---- Modal: detalhe + status ---- */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Reserva" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.guestName}</span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Mesa</dt>
                  <dd>{detail.tableLabel}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Pessoas</dt>
                  <dd>{detail.numPeople}</dd>
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
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{detail.guestPhone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Origem</dt>
                  <dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd>
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
              <p className="text-xs text-muted-foreground">Esta reserva está num status final.</p>
            )}
          </div>
        )}
      </Modal>

      {/* Confirmação de transição de status */}
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
