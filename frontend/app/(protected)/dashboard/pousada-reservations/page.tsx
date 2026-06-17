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
import { listRooms } from '@/lib/api/pousada/rooms'
import {
  createReservation,
  listReservations,
  updateReservationStatus,
} from '@/lib/api/pousada/reservations'
import {
  ALLOWED_NEXT,
  POUSADA_RESERVATION_STATUSES,
  statusLabel,
  type PousadaReservationStatusId,
} from '@/profiles/pousada/pousada-reservation-status'
import {
  computeNights,
  formatDate,
  formatPrice,
  type ConflictDetail,
  type Reservation,
} from '@/profiles/pousada/pousada-types'

function StatusBadge({ status }: { status: PousadaReservationStatusId }) {
  const variant =
    status === 'confirmado' ? 'success'
    : status === 'checked_in' ? 'info'
    : status === 'checked_out' ? 'muted'
    : status === 'reservado' ? 'warning'
    : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  roomId: string
  checkIn: string
  checkOut: string
  guestsCount: string
  guestName: string
  guestPhone: string
  notes: string
}
const EMPTY: FormState = { roomId: '', checkIn: '', checkOut: '', guestsCount: '2', guestName: '', guestPhone: '', notes: '' }

function groupByMonth(items: Reservation[]): { month: string; items: Reservation[] }[] {
  const groups: { month: string; items: Reservation[] }[] = []
  for (const r of items) {
    const [y, m] = r.checkInDate.split('-')
    const month = `${m}/${y}`
    const last = groups[groups.length - 1]
    if (last && last.month === month) last.items.push(r)
    else groups.push({ month, items: [r] })
  }
  return groups
}

/**
 * Reservas do PousadaBot (camada 7.6). Lista por mês com filtro de status e de quarto, criação
 * manual via Modal (calcula total em tempo real, trata 409 conflict_dates + 400 invalid_dates/
 * over_capacity/inactive_room) e detalhe com mudança de status.
 */
export default function PousadaReservationsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [roomFilter, setRoomFilter] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState<ConflictDetail | null>(null)

  const [detail, setDetail] = useState<Reservation | null>(null)
  const [statusTarget, setStatusTarget] = useState<PousadaReservationStatusId | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['pousada-reservations', status, roomFilter, page],
    queryFn: () => listReservations({
      status: status || undefined,
      roomId: roomFilter || undefined,
      page, pageSize: 50,
    }),
    placeholderData: keepPreviousData,
  })

  const rooms = useQuery({ queryKey: ['pousada-rooms-all'], queryFn: () => listRooms({ onlyActive: true }) })

  const selectedRoom = (rooms.data?.items ?? []).find((r) => r.id === form.roomId)
  const nights = computeNights(form.checkIn, form.checkOut)
  const liveTotal = selectedRoom && nights > 0 ? selectedRoom.nightlyRateCents * nights : null

  const createMutation = useMutation({
    mutationFn: () => createReservation({
      roomId: form.roomId,
      guestName: form.guestName,
      guestPhone: form.guestPhone || null,
      guestsCount: Math.round(Number(form.guestsCount)),
      checkIn: form.checkIn,
      checkOut: form.checkOut,
      notes: form.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pousada-reservations'] })
      setModalOpen(false); setForm(EMPTY); setFormError(null); setConflict(null)
    },
    onError: (e) => {
      setConflict(null)
      if (e instanceof ApiError && e.reason === 'conflict_dates') {
        setFormError('Esse quarto já está reservado nesse período.')
        const body = e.body as { conflict?: ConflictDetail } | null
        if (body?.conflict) setConflict(body.conflict)
      } else if (e instanceof ApiError && e.reason === 'invalid_dates') {
        setFormError('Datas inválidas (check-out deve ser depois do check-in e não no passado).')
      } else if (e instanceof ApiError && e.reason === 'over_capacity') {
        setFormError('Número de hóspedes acima da capacidade do quarto.')
      } else if (e instanceof ApiError && e.reason === 'inactive_room') {
        setFormError('Esse quarto está inativo.')
      } else {
        setFormError('Erro ao criar a reserva.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: PousadaReservationStatusId) => {
      if (!detail) throw new Error('sem reserva')
      return updateReservationStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['pousada-reservations'] })
      setStatusTarget(null); setDetail(updated)
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))
  const groups = groupByMonth(items)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reservas"
        description="Reservas da pousada. A IA reserva pelo WhatsApp; você também pode criar manualmente."
        actions={<Button onClick={() => { setForm(EMPTY); setFormError(null); setConflict(null); setModalOpen(true) }}>Nova reserva</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => { setStatus(''); setPage(0) }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todas
        </button>
        {POUSADA_RESERVATION_STATUSES.map((s) => (
          <button key={s.id} onClick={() => { setStatus(s.id); setPage(0) }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}>
            {s.label}
          </button>
        ))}
        <select value={roomFilter} onChange={(e) => { setRoomFilter(e.target.value); setPage(0) }}
          className="ml-auto rounded-md border border-border bg-background px-3 py-1.5 text-xs">
          <option value="">Todos os quartos</option>
          {(rooms.data?.items ?? []).map((r) => (
            <option key={r.id} value={r.id}>{r.name}</option>
          ))}
        </select>
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
            <section key={g.month} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.month}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((r) => (
                  <button key={r.id} onClick={() => setDetail(r)}
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{r.guestName}</span>
                        <StatusBadge status={r.status} />
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {r.roomName} · {formatDate(r.checkInDate)} → {formatDate(r.checkOutDate)} · {r.nights} noite(s) · {formatPrice(r.totalCents)}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">{r.guestsCount} hóspede(s)</span>
                  </button>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>Página {page + 1} de {totalPages} · {total} reserva(s)</span>
          <div className="flex gap-1">
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>←</Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}>→</Button>
          </div>
        </div>
      )}

      {/* Modal: nova reserva */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova reserva" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); createMutation.mutate() }}>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Quarto</label>
            <select value={form.roomId} onChange={(e) => setForm((f) => ({ ...f, roomId: e.target.value }))} required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
              <option value="">Selecione…</option>
              {(rooms.data?.items ?? []).map((r) => (
                <option key={r.id} value={r.id}>{r.name} (cap. {r.capacity}, {formatPrice(r.nightlyRateCents)}/noite)</option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Check-in</label>
              <input type="date" value={form.checkIn} onChange={(e) => setForm((f) => ({ ...f, checkIn: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Check-out</label>
              <input type="date" value={form.checkOut} onChange={(e) => setForm((f) => ({ ...f, checkOut: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Hóspedes</label>
              <input type="number" min="1" max={selectedRoom?.capacity ?? 20} value={form.guestsCount} required
                onChange={(e) => setForm((f) => ({ ...f, guestsCount: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone (opcional)</label>
              <input value={form.guestPhone} onChange={(e) => setForm((f) => ({ ...f, guestPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Hóspede titular</label>
            <input value={form.guestName} onChange={(e) => setForm((f) => ({ ...f, guestName: e.target.value }))} required
              maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
            <textarea value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {liveTotal != null && (
            <p className="rounded-md bg-muted/40 px-3 py-2 text-sm">
              {nights} noite(s) × {formatPrice(selectedRoom!.nightlyRateCents)} = <strong>{formatPrice(liveTotal)}</strong>
            </p>
          )}
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          {conflict && (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">
              Ocupado por <strong>{conflict.guestName}</strong>, {formatDate(conflict.checkInDate)} → {formatDate(conflict.checkOutDate)}.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Criando…' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + status */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Reserva" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.guestName}</span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-xs text-muted-foreground">Quarto</dt><dd>{detail.roomName}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Hóspedes</dt><dd>{detail.guestsCount}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Check-in</dt><dd>{formatDate(detail.checkInDate)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Check-out</dt><dd>{formatDate(detail.checkOutDate)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Noites</dt><dd>{detail.nights}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Total</dt><dd>{formatPrice(detail.totalCents)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Telefone</dt><dd>{detail.guestPhone ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Origem</dt><dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd></div>
                {detail.notes && <div className="col-span-2"><dt className="text-xs text-muted-foreground">Observações</dt><dd>{detail.notes}</dd></div>}
              </dl>
            </Card>

            {ALLOWED_NEXT[detail.status].length > 0 ? (
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Mudar status para…</label>
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[detail.status].map((next) => (
                    <Button key={next} variant="outline" className="h-8 px-3 text-xs"
                      onClick={() => setStatusTarget(next)}>
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

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O hóspede será notificado automaticamente em confirmação e cancelamento, se houver vínculo com o WhatsApp."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => { if (statusTarget) statusMutation.mutate(statusTarget) }}
      />
    </div>
  )
}
