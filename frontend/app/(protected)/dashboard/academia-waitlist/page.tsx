'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { listClasses } from '@/lib/api/academia/classes'
import { enqueueWaitlist, listWaitlist, updateWaitlistStatus } from '@/lib/api/academia/waitlist'
import { dayOfWeekLabel, formatTime, type WaitlistStatusId } from '@/profiles/academia/academia-types'

const STATUS_LABEL: Record<WaitlistStatusId, string> = {
  aguardando: 'Aguardando',
  chamado: 'Chamado',
  matriculado: 'Matriculado',
  desistiu: 'Desistiu',
}

function StatusBadge({ status }: { status: WaitlistStatusId }) {
  const variant = status === 'aguardando' ? 'warning'
    : status === 'chamado' ? 'success'
    : status === 'matriculado' ? 'info'
    : 'muted'
  return <Badge variant={variant}>{STATUS_LABEL[status]}</Badge>
}

/** Timestamp → "DD/MM HH:MM" pt-BR. */
function fmtSince(ts: string): string {
  return new Date(ts).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

type FormState = { studentName: string; studentPhone: string }
const EMPTY: FormState = { studentName: '', studentPhone: '' }

/**
 * Fila de espera por aula do AcademiaBot (camada 7.7). A posição é DERIVADA pelo backend
 * (ordem de entrada): quem sai da frente reordena todo mundo automaticamente, sem UPDATE.
 */
export default function AcademiaWaitlistPage() {
  const qc = useQueryClient()
  const [classId, setClassId] = useState('')
  const [showClosed, setShowClosed] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const classes = useQuery({ queryKey: ['academia-classes-all'], queryFn: () => listClasses({ onlyActive: true }) })

  const onlyWaiting = !showClosed
  const { data, isPending, isError } = useQuery({
    queryKey: ['academia-waitlist', classId, onlyWaiting],
    queryFn: () => listWaitlist(classId, { onlyWaiting }),
    enabled: classId !== '',
  })

  const enqueueMutation = useMutation({
    mutationFn: () => enqueueWaitlist({
      classId,
      studentName: form.studentName,
      studentPhone: form.studentPhone || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-waitlist'] })
      setModalOpen(false); setForm(EMPTY); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'already_waiting') {
        setFormError('Esse contato já está na fila desta aula.')
      } else {
        setFormError('Erro ao adicionar à fila.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: WaitlistStatusId }) => updateWaitlistStatus(id, status),
    onSuccess: () => {
      setActionError(null)
      qc.invalidateQueries({ queryKey: ['academia-waitlist'] })
      // matricular/desistir pode liberar vaga — atualiza os remainingSlots do select.
      qc.invalidateQueries({ queryKey: ['academia-classes-all'] })
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_status') {
        setActionError('Status inválido.')
      } else if (e instanceof ApiError && e.reason === 'already_waiting') {
        setActionError('Esse contato já está na fila desta aula.')
      } else {
        setActionError('Erro ao atualizar o status.')
      }
    },
  })

  const classItems = classes.data?.items ?? []
  const entries = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fila de espera"
        description="Fila por aula lotada. A posição é calculada na hora — matricular ou liberar alguém da frente reordena a fila automaticamente."
        actions={
          <Button disabled={classId === ''}
            onClick={() => { setForm(EMPTY); setFormError(null); setModalOpen(true) }}>
            Adicionar à fila
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="max-w-md flex-1">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Aula</label>
          <select value={classId} onChange={(e) => { setClassId(e.target.value); setActionError(null) }}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
            <option value="">Selecione uma aula…</option>
            {classItems.map((c) => (
              <option key={c.id} value={c.id}>
                {dayOfWeekLabel(c.dayOfWeek)} {formatTime(c.startTime)} · {c.modality} "{c.name}" — {c.remainingSlots} vaga(s)
              </option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 pb-2 text-xs text-muted-foreground">
          <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} />
          mostrar encerrados
        </label>
      </div>

      {actionError && <p className="text-sm text-destructive">{actionError}</p>}

      {classId === '' ? (
        <p className="text-sm text-muted-foreground">Escolha uma aula para ver a fila.</p>
      ) : isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a fila.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {onlyWaiting ? 'Ninguém aguardando nesta aula.' : 'Nenhuma entrada na fila desta aula.'}
        </p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {entries.map((entry) => (
            <div key={entry.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-3">
                {entry.status === 'aguardando' && (
                  <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-lg font-bold text-primary">
                    {entry.position}
                  </span>
                )}
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{entry.studentName}</span>
                    <StatusBadge status={entry.status} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {entry.studentPhone ? `${entry.studentPhone} · ` : ''}na fila desde {fmtSince(entry.enqueuedAt)}
                  </p>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {entry.status === 'aguardando' && (
                  <Button className="h-7 px-3 text-xs" disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate({ id: entry.id, status: 'chamado' })}>
                    Chamar
                  </Button>
                )}
                {entry.status === 'chamado' && (
                  <Button className="h-7 px-3 text-xs" disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate({ id: entry.id, status: 'matriculado' })}>
                    Matriculou
                  </Button>
                )}
                {(entry.status === 'aguardando' || entry.status === 'chamado') && (
                  <Button variant="outline" className="h-7 px-2 text-xs" disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate({ id: entry.id, status: 'desistiu' })}>
                    Desistiu
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Adicionar à fila" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); enqueueMutation.mutate() }}>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Aluno</label>
            <input value={form.studentName} onChange={(e) => setForm((f) => ({ ...f, studentName: e.target.value }))}
              required maxLength={200}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone (opcional)</label>
            <input value={form.studentPhone} onChange={(e) => setForm((f) => ({ ...f, studentPhone: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={enqueueMutation.isPending}>
              {enqueueMutation.isPending ? 'Adicionando…' : 'Adicionar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
