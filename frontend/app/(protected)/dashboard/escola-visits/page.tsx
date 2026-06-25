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
import { listStudents } from '@/lib/api/escola/students'
import { createVisit, listVisits, updateVisitStatus } from '@/lib/api/escola/visits'
import {
  ALLOWED_NEXT,
  ESCOLA_VISIT_STATUSES,
  statusLabel,
  type EscolaVisitStatusId,
} from '@/profiles/escola/escola-visit-status'
import {
  PERIODS,
  formatDate,
  periodLabel,
  type EscolaPeriod,
  type EscolaVisit,
} from '@/profiles/escola/escola-types'

function StatusBadge({ status }: { status: EscolaVisitStatusId }) {
  const variant = status === 'agendada' ? 'info' : status === 'realizada' ? 'success' : 'muted'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type FormState = {
  visitorName: string
  visitorPhone: string
  visitDate: string
  period: EscolaPeriod
  numPeople: string
  studentId: string
  notes: string
}
const EMPTY: FormState = { visitorName: '', visitorPhone: '', visitDate: '', period: 'manha', numPeople: '', studentId: '', notes: '' }

/**
 * Visitas do EscolaBot (camada 8.19). Agenda leve por data + período (manhã/tarde). Lista por status,
 * criação manual (data, período, nº de pessoas, aluno opcional), e marca realizada/cancelada.
 * A IA também agenda visitas pelo WhatsApp.
 */
export default function EscolaVisitsPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const [detail, setDetail] = useState<EscolaVisit | null>(null)
  const [statusTarget, setStatusTarget] = useState<EscolaVisitStatusId | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['escola-visits', status, page],
    queryFn: () => listVisits({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const students = useQuery({ queryKey: ['escola-students-all'], queryFn: () => listStudents({ active: true }) })

  const createMutation = useMutation({
    mutationFn: () => createVisit({
      visitorName: form.visitorName,
      visitorPhone: form.visitorPhone || null,
      visitDate: form.visitDate,
      period: form.period,
      numPeople: form.numPeople.trim() === '' ? null : Math.round(Number(form.numPeople)),
      studentId: form.studentId || null,
      notes: form.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['escola-visits'] })
      setModalOpen(false); setForm(EMPTY); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_date') {
        setFormError('Data inválida — não pode ser no passado.')
      } else {
        setFormError('Erro ao agendar a visita.')
      }
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: EscolaVisitStatusId) => {
      if (!detail) throw new Error('sem visita')
      return updateVisitStatus(detail.id, newStatus)
    },
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['escola-visits'] })
      setStatusTarget(null); setDetail(updated)
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Visitas"
        description="Visitas agendadas à escola (data + período). A IA agenda pelo WhatsApp; você também pode criar manualmente."
        actions={<Button onClick={() => { setForm(EMPTY); setFormError(null); setModalOpen(true) }}>Nova visita</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => { setStatus(''); setPage(0) }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}>
          Todas
        </button>
        {ESCOLA_VISIT_STATUSES.map((s) => (
          <button key={s.id} onClick={() => { setStatus(s.id); setPage(0) }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}>
            {s.label}
          </button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as visitas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma visita encontrada.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((v) => (
            <button key={v.id} onClick={() => setDetail(v)}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{v.visitorName}</span>
                  <StatusBadge status={v.status} />
                </div>
                <p className="text-xs text-muted-foreground">
                  {formatDate(v.visitDate)} · {periodLabel(v.period)}
                  {v.numPeople ? ` · ${v.numPeople} pessoa(s)` : ''}
                </p>
              </div>
              <span className="text-xs text-muted-foreground">{v.conversationId ? 'WhatsApp' : 'Manual'}</span>
            </button>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>Página {page + 1} de {totalPages} · {total} visita(s)</span>
          <div className="flex gap-1">
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>←</Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}>→</Button>
          </div>
        </div>
      )}

      {/* Modal: nova visita */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Nova visita" size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); createMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Visitante</label>
              <input value={form.visitorName} onChange={(e) => setForm((f) => ({ ...f, visitorName: e.target.value }))} required
                maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Telefone (opcional)</label>
              <input value={form.visitorPhone} onChange={(e) => setForm((f) => ({ ...f, visitorPhone: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data</label>
              <input type="date" value={form.visitDate} onChange={(e) => setForm((f) => ({ ...f, visitDate: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Período</label>
              <select value={form.period} onChange={(e) => setForm((f) => ({ ...f, period: e.target.value as EscolaPeriod }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                {PERIODS.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nº pessoas</label>
              <input type="number" min="1" max="50" value={form.numPeople}
                onChange={(e) => setForm((f) => ({ ...f, numPeople: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Aluno (opcional)</label>
            <select value={form.studentId} onChange={(e) => setForm((f) => ({ ...f, studentId: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
              <option value="">Nenhum</option>
              {(students.data?.items ?? []).map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Observações</label>
            <textarea value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={createMutation.isPending || !form.visitDate}>
              {createMutation.isPending ? 'Agendando…' : 'Agendar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + status */}
      <Modal open={detail !== null} onClose={() => setDetail(null)} title="Visita" size="md">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{detail.visitorName}</span>
              <StatusBadge status={detail.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-xs text-muted-foreground">Data</dt><dd>{formatDate(detail.visitDate)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Período</dt><dd>{periodLabel(detail.period)}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Pessoas</dt><dd>{detail.numPeople ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Telefone</dt><dd>{detail.visitorPhone ?? '—'}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Origem</dt><dd>{detail.conversationId ? 'WhatsApp' : 'Manual'}</dd></div>
                {detail.notes && <div className="col-span-2"><dt className="text-xs text-muted-foreground">Observações</dt><dd>{detail.notes}</dd></div>}
              </dl>
            </Card>

            {ALLOWED_NEXT[detail.status].length > 0 && (
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Mudar status para…</label>
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[detail.status].map((next) => (
                    <Button key={next} variant="outline" className="h-8 px-3 text-xs" onClick={() => setStatusTarget(next)}>
                      {statusLabel(next)}
                    </Button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="A mudança de status é registrada na visita."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => { if (statusTarget) statusMutation.mutate(statusTarget) }}
      />
    </div>
  )
}
