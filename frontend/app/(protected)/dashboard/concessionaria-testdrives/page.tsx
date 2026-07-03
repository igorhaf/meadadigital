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
import { listSalespeople } from '@/lib/api/concessionaria/salespeople'
import {
  createTestDrive,
  listTestDrives,
  updateTestDriveStatus,
} from '@/lib/api/concessionaria/testdrives'
import { listVehicles } from '@/lib/api/concessionaria/vehicles'
import {
  ALLOWED_NEXT,
  statusLabel,
  TEST_DRIVE_STATUSES,
  type TestDriveStatusId,
} from '@/profiles/concessionaria/concessionaria-test-drive-status'
import {
  formatDate,
  formatTime,
  type ConflictDetail,
  type TestDrive,
} from '@/profiles/concessionaria/concessionaria-types'

function StatusBadge({ status }: { status: TestDriveStatusId }) {
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
  vehicleId: string
  salespersonId: string
  customerName: string
  date: string
  time: string
  notes: string
}
const EMPTY: FormState = {
  vehicleId: '',
  salespersonId: '',
  customerName: '',
  date: '',
  time: '',
  notes: '',
}

/** Agrupa test-drives por dia (ordem já vem por start_at asc da API). */
function groupByDay(items: TestDrive[]): { day: string; items: TestDrive[] }[] {
  const groups: { day: string; items: TestDrive[] }[] = []
  for (const t of items) {
    const day = formatDate(t.startAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(t)
    else groups.push({ day, items: [t] })
  }
  return groups
}

/**
 * Agenda de test-drives do ConcessionariaBot (camada 8.17). Lista por dia com filtro de status,
 * criação manual via Modal (vehicleId + salespersonId + startAt; trata 409 conflict_slot e 422
 * vehicle_not_available) e detalhe com mudança de status (ALLOWED_NEXT).
 */
export default function ConcessionariaTestDrivesPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState<ConflictDetail | null>(null)

  const [detail, setDetail] = useState<TestDrive | null>(null)
  const [statusTarget, setStatusTarget] = useState<TestDriveStatusId | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-testdrives', status, page],
    queryFn: () => listTestDrives({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const vehicles = useQuery({
    queryKey: ['concessionaria-vehicles-available'],
    queryFn: () => listVehicles({ available: true }),
  })
  const salespeople = useQuery({
    queryKey: ['concessionaria-salespeople-active'],
    queryFn: () => listSalespeople({ onlyActive: true }),
  })

  const createMutation = useMutation({
    mutationFn: () => {
      const startAt = new Date(`${form.date}T${form.time}:00`).toISOString()
      return createTestDrive({
        vehicleId: form.vehicleId,
        salespersonId: form.salespersonId,
        startAt,
        customerName: form.customerName || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['concessionaria-testdrives'] })
      setModalOpen(false)
      setForm(EMPTY)
      setFormError(null)
      setConflict(null)
    },
    onError: (e) => {
      setConflict(null)
      if (e instanceof ApiError && e.reason === 'conflict_slot') {
        setFormError('Esse horário já está ocupado na agenda.')
        const body = e.body as { conflict?: ConflictDetail } | null
        if (body?.conflict) setConflict(body.conflict)
      } else if (e instanceof ApiError && e.reason === 'vehicle_not_available') {
        setFormError('Esse veículo não está disponível para test-drive.')
      } else if (e instanceof ApiError && e.reason === 'outside_hours') {
        setFormError('Esse horário está fora do funcionamento da loja.')
      } else {
        setFormError('Erro ao agendar o test-drive.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: TestDriveStatusId) => {
      if (!detail) throw new Error('sem test-drive')
      return updateTestDriveStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['concessionaria-testdrives'] })
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
        title="Test-drives"
        description="Agenda de test-drives. A IA agenda pelo WhatsApp; você também pode criar manualmente."
        actions={
          <Button
            onClick={() => {
              setForm(EMPTY)
              setFormError(null)
              setConflict(null)
              setModalOpen(true)
            }}
          >
            Novo test-drive
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
        {TEST_DRIVE_STATUSES.map((s) => (
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
        <p className="text-sm text-destructive">Erro ao carregar os test-drives.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum test-drive encontrado.</p>
      ) : (
        <div className="space-y-6">
          {groups.map((g) => (
            <section key={g.day} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{g.day}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => setDetail(t)}
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">
                          {t.vehicleBrand} {t.vehicleModel}
                          {t.vehicleYear ? ` ${t.vehicleYear}` : ''}
                        </span>
                        <StatusBadge status={t.status} />
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {t.customerName ?? 'Cliente'} · {formatTime(t.startAt)}–
                        {formatTime(t.endAt)}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">{formatTime(t.startAt)}</span>
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
            Página {page + 1} de {totalPages} · {total} test-drive(s)
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

      {/* Modal: novo test-drive */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo test-drive" size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Veículo (disponível)
            </label>
            <select
              value={form.vehicleId}
              onChange={(e) => setForm((f) => ({ ...f, vehicleId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(vehicles.data?.items ?? []).map((v) => (
                <option key={v.id} value={v.id}>
                  {v.brand} {v.model}
                  {v.modelYear ? ` ${v.modelYear}` : ''}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Vendedor</label>
            <select
              value={form.salespersonId}
              onChange={(e) => setForm((f) => ({ ...f, salespersonId: e.target.value }))}
              required
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="">Selecione…</option>
              {(salespeople.data?.items ?? []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Cliente</label>
            <input
              value={form.customerName}
              onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))}
              placeholder="Nome do cliente"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
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
              Ocupado
              {conflict.customerName ? (
                <>
                  {' '}
                  por <strong>{conflict.customerName}</strong>
                </>
              ) : (
                ''
              )}
              , das {formatTime(conflict.startAt)} às {formatTime(conflict.endAt)}.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Agendando…' : 'Agendar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + status */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Test-drive" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">
                {detail.vehicleBrand} {detail.vehicleModel}
                {detail.vehicleYear ? ` ${detail.vehicleYear}` : ''}
              </span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Cliente</dt>
                  <dd>{detail.customerName ?? '—'}</dd>
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
              <p className="text-xs text-muted-foreground">
                Este test-drive está num status final.
              </p>
            )}
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O cliente é notificado automaticamente em confirmação e cancelamento, se houver vínculo com o WhatsApp."
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
