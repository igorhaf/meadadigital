'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { listCases } from '@/lib/api/legal/cases'
import {
  createDeadline,
  deleteDeadline,
  listDeadlines,
  updateDeadline,
  type LegalDeadline,
} from '@/lib/api/legal/deadlines'

type FormState = {
  caseId: string
  kind: string
  title: string
  dueDate: string
  dueTime: string
  location: string
  notes: string
}
const EMPTY: FormState = {
  caseId: '',
  kind: 'prazo',
  title: '',
  dueDate: '',
  dueTime: '',
  location: '',
  notes: '',
}

const KIND_LABEL: Record<string, string> = { prazo: 'Prazo', audiencia: 'Audiência' }
const STATUS_LABEL: Record<string, string> = {
  pendente: 'Pendente',
  cumprido: 'Cumprido',
  perdido: 'Perdido',
}

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

/**
 * Agenda de prazos e audiências do escritório (onda Legal 1, backlog #1). CRUD via Modal +
 * mudança de status inline. O lembrete D-3/D-1 ao cliente vinculado sai do scheduler
 * (LegalDeadlineReminderJob) — texto com data/hora/local, nunca mérito.
 */
export default function LegalDeadlinesPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState('pendente')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<LegalDeadline | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['legal-deadlines', statusFilter],
    queryFn: () => listDeadlines(statusFilter ? { status: statusFilter } : {}),
  })

  const { data: casesData } = useQuery({
    queryKey: ['legal-cases-for-deadlines'],
    queryFn: () => listCases({ pageSize: 100 }),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        kind: form.kind,
        title: form.title.trim(),
        dueDate: form.dueDate,
        dueTime: form.dueTime || null,
        location: form.location.trim() || null,
        notes: form.notes.trim() || null,
      }
      if (editing) {
        return updateDeadline(editing.id, { ...payload, clearDueTime: !form.dueTime })
      }
      return createDeadline({ ...payload, caseId: form.caseId })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['legal-deadlines'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_date') setFormError('Data inválida.')
      else if (e instanceof ApiError && e.reason === 'case_not_found')
        setFormError('Processo não encontrado.')
      else setFormError('Erro ao salvar.')
    },
  })

  const [statusError, setStatusError] = useState<string | null>(null)
  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => updateDeadline(id, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['legal-deadlines'] })
      setStatusError(null)
    },
    onError: (e) => {
      // Falha silenciosa deixava o tenant sem saber que a ação não valeu; re-busca o estado real.
      qc.invalidateQueries({ queryKey: ['legal-deadlines'] })
      setStatusError(
        e instanceof ApiError && e.reason === 'invalid_status_transition'
          ? 'O status já mudou em outra tela — a lista foi atualizada.'
          : 'Erro ao atualizar o status. Tente novamente.',
      )
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteDeadline(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['legal-deadlines'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(d: LegalDeadline) {
    setEditing(d)
    setForm({
      caseId: d.caseId,
      kind: d.kind,
      title: d.title,
      dueDate: d.dueDate,
      dueTime: d.dueTime?.slice(0, 5) ?? '',
      location: d.location ?? '',
      notes: d.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []
  const cases = casesData?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Prazos e audiências"
        description="A agenda do escritório. O cliente vinculado recebe lembrete automático 3 dias e 1 dia antes (data e local, sem mérito)."
        actions={<Button onClick={openCreate}>Novo prazo</Button>}
      />

      {statusError && <p className="text-sm text-destructive">{statusError}</p>}

      <div className="flex gap-2">
        {['pendente', 'cumprido', 'perdido', ''].map((s) => (
          <Button
            key={s || 'todos'}
            variant={statusFilter === s ? 'default' : 'outline'}
            className="h-7 px-3 text-xs"
            onClick={() => setStatusFilter(s)}
          >
            {s ? STATUS_LABEL[s] : 'Todos'}
          </Button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os prazos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum prazo nesse filtro.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((d) => (
            <div key={d.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <Badge variant={d.kind === 'audiencia' ? 'info' : 'muted'}>
                    {KIND_LABEL[d.kind]}
                  </Badge>
                  <span className="truncate font-medium">{d.title}</span>
                </div>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">
                  {formatDate(d.dueDate)}
                  {d.dueTime ? ` às ${d.dueTime.slice(0, 5)}` : ''}
                  {d.location ? ` · ${d.location}` : ''} · {d.caseTitle}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <select
                  value={d.status}
                  disabled={statusMutation.isPending}
                  onChange={(e) => statusMutation.mutate({ id: d.id, status: e.target.value })}
                  className="rounded-md border border-border bg-background px-2 py-1 text-xs"
                >
                  {Object.entries(STATUS_LABEL).map(([id, label]) => (
                    <option key={id} value={id}>
                      {label}
                    </option>
                  ))}
                </select>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(d)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(d.id)}
                >
                  Excluir
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar prazo' : 'Novo prazo'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          {!editing && (
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Processo
              </label>
              <select
                value={form.caseId}
                required
                onChange={(e) => setForm((f) => ({ ...f, caseId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {cases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title} ({c.cnjNumberFormatted})
                  </option>
                ))}
              </select>
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Tipo</label>
              <select
                value={form.kind}
                onChange={(e) => setForm((f) => ({ ...f, kind: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="prazo">Prazo</option>
                <option value="audiencia">Audiência</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
              <input
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                required
                maxLength={200}
                placeholder="Contestação, audiência de instrução…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Data</label>
              <input
                type="date"
                value={form.dueDate}
                required
                onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Hora (opcional)
              </label>
              <input
                type="time"
                value={form.dueTime}
                onChange={(e) => setForm((f) => ({ ...f, dueTime: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Local (opcional)
              </label>
              <input
                value={form.location}
                onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
                placeholder="Fórum Central, sala 3…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações (internas)
            </label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
