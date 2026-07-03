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
import { listBarbers } from '@/lib/api/barbearia/barbers'
import { listServices } from '@/lib/api/barbearia/services'
import {
  createAppointment,
  listAppointments,
  updateAppointmentStatus,
} from '@/lib/api/barbearia/appointments'
import {
  ALLOWED_NEXT,
  BARBER_APPOINTMENT_STATUSES,
  statusLabel,
  type BarberAppointmentStatusId,
} from '@/profiles/barbearia/barber-appointment-status'
import {
  formatDate,
  formatPrice,
  formatTime,
  type Appointment,
  type ConflictDetail,
} from '@/profiles/barbearia/barber-types'

function StatusBadge({ status }: { status: BarberAppointmentStatusId }) {
  const variant =
    status === 'confirmado' ? 'success'
    : status === 'realizado' ? 'info'
    : status === 'agendado' ? 'warning'
    : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  barberId: string
  serviceId: string
  date: string
  time: string
  guestName: string
  guestPhone: string
  notes: string
}
const EMPTY: FormState = { barberId: '', serviceId: '', date: '', time: '', guestName: '', guestPhone: '', notes: '' }

function groupByDay(items: Appointment[]): { day: string; items: Appointment[] }[] {
  const groups: { day: string; items: Appointment[] }[] = []
  for (const a of items) {
    const day = formatDate(a.startAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(a)
    else groups.push({ day, items: [a] })
  }
  return groups
}

/**
 * Agenda do BarbeariaBot (camada 8.1, clone do salon). Lista por dia com filtro de status e de
 * barbeiro, criação manual via Modal (trata 409 conflict_slot por barbeiro + 400 inactive_*) e
 * detalhe com mudança de status (notifica cliente em confirmado/cancelado).
 */
export default function BarberAppointmentsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [barberFilter, setBarberFilter] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState<ConflictDetail | null>(null)

  const [detail, setDetail] = useState<Appointment | null>(null)
  const [statusTarget, setStatusTarget] = useState<BarberAppointmentStatusId | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['barber-appointments', status, barberFilter, page],
    queryFn: () => listAppointments({
      status: status || undefined,
      barberId: barberFilter || undefined,
      page, pageSize: 50,
    }),
    placeholderData: keepPreviousData,
  })

  const barbers = useQuery({ queryKey: ['barber-barbers-all'], queryFn: () => listBarbers({ onlyActive: true }) })
  const services = useQuery({ queryKey: ['barber-services-all'], queryFn: () => listServices({ onlyActive: true }) })

  const createMutation = useMutation({
    mutationFn: () => {
      const startAt = new Date(`${form.date}T${form.time}:00`).toISOString()
      return createAppointment({
        barberId: form.barberId,
        serviceId: form.serviceId,
        guestName: form.guestName,
        guestPhone: form.guestPhone || null,
        startAt,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['barber-appointments'] })
      setModalOpen(false); setForm(EMPTY); setFormError(null); setConflict(null)
    },
    onError: (e) => {
      setConflict(null)
      if (e instanceof ApiError && e.reason === 'conflict_slot') {
        setFormError('Esse barbeiro já tem agendamento nesse horário.')
        const body = e.body as { conflict?: ConflictDetail } | null
        if (body?.conflict) setConflict(body.conflict)
      } else if (e instanceof ApiError && e.reason === 'outside_hours') {
        setFormError('Esse horário está fora do funcionamento da barbearia.')
      } else if (e instanceof ApiError && e.reason === 'inactive_barber') {
        setFormError('Esse barbeiro está inativo.')
      } else if (e instanceof ApiError && e.reason === 'inactive_service') {
        setFormError('Esse serviço está inativo.')
      } else {
        setFormError('Erro ao criar o agendamento.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: BarberAppointmentStatusId) => {
      if (!detail) throw new Error('sem agendamento')
      return updateAppointmentStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['barber-appointments'] })
      setStatusTarget(null); setDetail(updated)
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
        description="Horários marcados. A IA marca pelo WhatsApp; você também pode criar manualmente."
        actions={<Button onClick={() => { setForm(EMPTY); setFormError(null); setConflict(null); setModalOpen(true) }}>Novo horário</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => { setStatus(''); setPage(0) }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todos
        </button>
        {BARBER_APPOINTMENT_STATUSES.map((s) => (
          <button key={s.id} onClick={() => { setStatus(s.id); setPage(0) }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}>
            {s.label}
          </button>
        ))}
        <select value={barberFilter} onChange={(e) => { setBarberFilter(e.target.value); setPage(0) }}
          className="ml-auto rounded-md border border-border bg-background px-3 py-1.5 text-xs">
          <option value="">Todos os barbeiros</option>
          {(barbers.data?.items ?? []).map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a agenda.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum horário encontrado.</p>
      ) : (
        <div className="space-y-6">
          {groups.map((g) => (
            <section key={g.day} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.day}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((a) => (
                  <button key={a.id} onClick={() => setDetail(a)}
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{a.guestName}</span>
                        <StatusBadge status={a.status} />
                        {a.loyaltyApplied && <Badge variant="success">grátis · fidelidade</Badge>}
                        {a.couponCodeSnapshot && <Badge variant="info">cupom {a.couponCodeSnapshot}</Badge>}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {a.serviceName} · {a.barberName} · {formatTime(a.startAt)}–{formatTime(a.endAt)}
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
          <span>Página {page + 1} de {totalPages} · {total} horário(s)</span>
          <div className="flex gap-1">
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>←</Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}>→</Button>
          </div>
        </div>
      )}

      {/* Modal: novo horário */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo horário" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); createMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Barbeiro</label>
              <select value={form.barberId} onChange={(e) => setForm((f) => ({ ...f, barberId: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="">Selecione…</option>
                {(barbers.data?.items ?? []).map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Serviço</label>
              <select value={form.serviceId} onChange={(e) => setForm((f) => ({ ...f, serviceId: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="">Selecione…</option>
                {(services.data?.items ?? []).map((o) => (
                  <option key={o.id} value={o.id}>{o.name} ({o.durationMinutes}min)</option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data</label>
              <input type="date" value={form.date} onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Hora</label>
              <input type="time" value={form.time} onChange={(e) => setForm((f) => ({ ...f, time: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
              <input value={form.guestName} onChange={(e) => setForm((f) => ({ ...f, guestName: e.target.value }))} required
                maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone (opcional)</label>
              <input value={form.guestPhone} onChange={(e) => setForm((f) => ({ ...f, guestPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
            <textarea value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          {conflict && (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">
              Ocupado por <strong>{conflict.guestName}</strong>, das {formatTime(conflict.startAt)} às {formatTime(conflict.endAt)}.
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
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Horário" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.guestName}</span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-xs text-muted-foreground">Serviço</dt><dd>{detail.serviceName}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Barbeiro</dt><dd>{detail.barberName}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Data</dt><dd>{formatDate(detail.startAt)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Horário</dt><dd>{formatTime(detail.startAt)}–{formatTime(detail.endAt)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Telefone</dt><dd>{detail.guestPhone ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Origem</dt><dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd></div>
                <div>
                  <dt className="text-xs text-muted-foreground">Valor</dt>
                  <dd>
                    {detail.priceCents == null ? '—' : formatPrice(detail.priceCents - detail.discountCents)}
                    {detail.loyaltyApplied && <span className="ml-1 text-xs text-emerald-600">grátis (fidelidade)</span>}
                    {detail.couponCodeSnapshot && !detail.loyaltyApplied && detail.discountCents > 0 && (
                      <span className="ml-1 text-xs text-muted-foreground">
                        (cupom {detail.couponCodeSnapshot}: −{formatPrice(detail.discountCents)})
                      </span>
                    )}
                  </dd>
                </div>
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
              <p className="text-xs text-muted-foreground">Este horário está num status final.</p>
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
        onConfirm={() => { if (statusTarget) statusMutation.mutate(statusTarget) }}
      />
    </div>
  )
}
