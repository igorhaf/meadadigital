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
  createAppointment,
  getSessionNote,
  listAppointments,
  updateAppointmentStatus,
  upsertSessionNote,
} from '@/lib/api/estetica/appointments'
import { listProcedures } from '@/lib/api/estetica/procedures'
import { listProfessionals } from '@/lib/api/estetica/professionals'
import {
  AESTHETIC_APPOINTMENT_STATUSES,
  ALLOWED_NEXT,
  statusLabel,
  type AestheticAppointmentStatusId,
} from '@/profiles/estetica/aesthetic-appointment-status'
import { formatDateTime, type AestheticAppointment } from '@/profiles/estetica/estetica-types'

function StatusBadge({ status }: { status: AestheticAppointmentStatusId }) {
  const variant =
    status === 'confirmado' || status === 'realizado'
      ? 'success'
      : status === 'cancelado' || status === 'falta'
        ? 'muted'
        : 'default'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type CreateForm = {
  professionalId: string
  procedureId: string
  guestName: string
  guestPhone: string
  date: string
  time: string
  notes: string
}
const EMPTY_CREATE: CreateForm = {
  professionalId: '',
  procedureId: '',
  guestName: '',
  guestPhone: '',
  date: '',
  time: '',
  notes: '',
}

type NoteForm = { treatedArea: string; deviceParams: string; observations: string }
const EMPTY_NOTE: NoteForm = { treatedArea: '', deviceParams: '', observations: '' }

/**
 * Agenda do EsteticaBot (camada 8.3). Lista por dia, criação manual (avulsa — pacote é consumido
 * pela IA via conversa), transição de status (cancelar devolve a sessão ao pacote), e a ficha/
 * evolução inline por sessão.
 */
export default function EsteticaAppointmentsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState<CreateForm>(EMPTY_CREATE)
  const [createError, setCreateError] = useState<string | null>(null)

  const [noteFor, setNoteFor] = useState<AestheticAppointment | null>(null)
  const [noteForm, setNoteForm] = useState<NoteForm>(EMPTY_NOTE)
  const [noteError, setNoteError] = useState<string | null>(null)

  const [statusTarget, setStatusTarget] = useState<{
    id: string
    next: AestheticAppointmentStatusId
  } | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['estetica-appointments', status, page],
    queryFn: () => listAppointments({ status: status || undefined, page, pageSize: 100 }),
    placeholderData: keepPreviousData,
  })
  const professionals = useQuery({
    queryKey: ['estetica-professionals-all'],
    queryFn: () => listProfessionals({ onlyActive: true }),
  })
  const procedures = useQuery({
    queryKey: ['estetica-procedures-all'],
    queryFn: () => listProcedures({ onlyActive: true }),
  })

  const createMutation = useMutation({
    mutationFn: () => {
      const startAt = new Date(`${createForm.date}T${createForm.time}:00`).toISOString()
      return createAppointment({
        professionalId: createForm.professionalId,
        procedureId: createForm.procedureId,
        guestName: createForm.guestName,
        guestPhone: createForm.guestPhone || null,
        startAt,
        notes: createForm.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['estetica-appointments'] })
      setCreateOpen(false)
      setCreateForm(EMPTY_CREATE)
      setCreateError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'conflict_slot')
        setCreateError('Esse horário já está ocupado para o profissional.')
      else if (e instanceof ApiError && e.reason === 'outside_hours')
        setCreateError('Fora do horário de funcionamento.')
      else if (e instanceof ApiError && e.reason === 'inactive_professional')
        setCreateError('Profissional inativo.')
      else if (e instanceof ApiError && e.reason === 'inactive_procedure')
        setCreateError('Procedimento inativo.')
      else setCreateError('Erro ao criar o agendamento.')
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, next }: { id: string; next: AestheticAppointmentStatusId }) =>
      updateAppointmentStatus(id, next),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['estetica-appointments'] })
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'invalid_status_transition')
        setStatusError('Transição de status inválida.')
      else setStatusError('Erro ao mudar o status.')
    },
  })

  const noteMutation = useMutation({
    mutationFn: () => {
      if (!noteFor) throw new Error('sem agendamento')
      return upsertSessionNote(noteFor.id, {
        treatedArea: noteForm.treatedArea || null,
        deviceParams: noteForm.deviceParams || null,
        observations: noteForm.observations || null,
      })
    },
    onSuccess: () => {
      setNoteFor(null)
      setNoteError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'appointment_cancelled')
        setNoteError('Agendamento cancelado não tem ficha.')
      else setNoteError('Erro ao salvar a ficha.')
    },
  })

  async function openNote(a: AestheticAppointment) {
    setNoteFor(a)
    setNoteError(null)
    setNoteForm(EMPTY_NOTE)
    try {
      const existing = await getSessionNote(a.id)
      setNoteForm({
        treatedArea: existing.treatedArea ?? '',
        deviceParams: existing.deviceParams ?? '',
        observations: existing.observations ?? '',
      })
    } catch {
      // sem ficha ainda — começa vazia.
    }
  }

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 100))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Agenda"
        description="A IA agenda sessões pelo WhatsApp (consumindo pacotes); aqui você gerencia a agenda e a ficha de cada sessão."
        actions={
          <Button
            onClick={() => {
              setCreateForm(EMPTY_CREATE)
              setCreateError(null)
              setCreateOpen(true)
            }}
          >
            Novo agendamento
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
        {AESTHETIC_APPOINTMENT_STATUSES.map((s) => (
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
        <p className="text-sm text-destructive">Erro ao carregar a agenda.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum agendamento encontrado.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((a: AestheticAppointment) => (
            <div key={a.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{a.guestName}</span>
                  <span className="text-xs text-muted-foreground">{a.procedureName}</span>
                  <StatusBadge status={a.status} />
                  {a.consumedSession && <Badge variant="info">pacote</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(a.startAt)} · {a.professionalName}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {a.status !== 'cancelado' && (
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    onClick={() => openNote(a)}
                  >
                    Ficha
                  </Button>
                )}
                {ALLOWED_NEXT[a.status].map((n) => (
                  <Button
                    key={n}
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    onClick={() => setStatusTarget({ id: a.id, next: n })}
                  >
                    {statusLabel(n)}
                  </Button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} agendamentos
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
      {statusError && <p className="text-sm text-destructive">{statusError}</p>}

      {/* Novo agendamento (avulso) */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Novo agendamento"
        size="md"
      >
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
                Profissional
              </label>
              <select
                value={createForm.professionalId}
                onChange={(e) => setCreateForm((f) => ({ ...f, professionalId: e.target.value }))}
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
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Procedimento
              </label>
              <select
                value={createForm.procedureId}
                onChange={(e) => setCreateForm((f) => ({ ...f, procedureId: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {(procedures.data?.items ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.durationMinutes}min)
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Cliente
              </label>
              <input
                value={createForm.guestName}
                onChange={(e) => setCreateForm((f) => ({ ...f, guestName: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Telefone (opcional)
              </label>
              <input
                value={createForm.guestPhone}
                onChange={(e) => setCreateForm((f) => ({ ...f, guestPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data</label>
              <input
                type="date"
                value={createForm.date}
                onChange={(e) => setCreateForm((f) => ({ ...f, date: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Hora</label>
              <input
                type="time"
                value={createForm.time}
                onChange={(e) => setCreateForm((f) => ({ ...f, time: e.target.value }))}
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
              value={createForm.notes}
              onChange={(e) => setCreateForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Agendamento manual é avulso (não consome pacote). O consumo de pacote acontece quando a
            IA agenda pela conversa.
          </p>
          {createError && <p className="text-sm text-destructive">{createError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Agendando…' : 'Agendar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Ficha/evolução por sessão */}
      <Modal
        open={noteFor !== null}
        onClose={() => setNoteFor(null)}
        title="Ficha da sessão"
        size="md"
      >
        {noteFor && (
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault()
              noteMutation.mutate()
            }}
          >
            <Card>
              <p className="text-sm">
                <span className="font-medium">{noteFor.guestName}</span> · {noteFor.procedureName}
              </p>
              <p className="text-xs text-muted-foreground">
                {formatDateTime(noteFor.startAt)} · {noteFor.professionalName}
              </p>
            </Card>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Área tratada
              </label>
              <input
                value={noteForm.treatedArea}
                onChange={(e) => setNoteForm((f) => ({ ...f, treatedArea: e.target.value }))}
                placeholder="rosto, axila, abdômen…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Parâmetros do aparelho
              </label>
              <input
                value={noteForm.deviceParams}
                onChange={(e) => setNoteForm((f) => ({ ...f, deviceParams: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Observações
              </label>
              <textarea
                value={noteForm.observations}
                onChange={(e) => setNoteForm((f) => ({ ...f, observations: e.target.value }))}
                rows={3}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <p className="text-xs text-muted-foreground">
              Registro administrativo da sessão (sem dado clínico sensível). Sem foto nesta versão.
            </p>
            {noteError && <p className="text-sm text-destructive">{noteError}</p>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setNoteFor(null)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={noteMutation.isPending}>
                {noteMutation.isPending ? 'Salvando…' : 'Salvar ficha'}
              </Button>
            </div>
          </form>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget.next) : ''}"?`}
        description="Cancelar um agendamento que consumiu uma sessão de pacote DEVOLVE a sessão ao saldo. O cliente é notificado em confirmação e cancelamento (se houver vínculo com o WhatsApp)."
        confirmLabel="Mudar status"
        destructive={statusTarget?.next === 'cancelado'}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusTarget) statusMutation.mutate(statusTarget)
        }}
      />
    </div>
  )
}
