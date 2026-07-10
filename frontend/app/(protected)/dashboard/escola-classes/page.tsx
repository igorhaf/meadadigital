'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createClass,
  deleteClass,
  listClasses,
  toggleClass,
  updateClass,
} from '@/lib/api/escola/classes'
import { listWaitlist, notifyOpening, updateWaitlistStatus } from '@/lib/api/escola/waitlist'
import {
  formatBrl,
  shiftLabel,
  SHIFTS,
  type EscolaClass,
  type EscolaShift,
} from '@/profiles/escola/escola-types'

type FormState = {
  name: string
  grade: string
  shift: EscolaShift
  capacity: string
  monthly: string
  year: string
  description: string
}
const EMPTY: FormState = {
  name: '',
  grade: '',
  shift: 'manha',
  capacity: '20',
  monthly: '',
  year: String(new Date().getFullYear()),
  description: '',
}

/**
 * Turmas do EscolaBot (camada 8.19). CRUD via Modal: nome, série, turno (manhã/tarde/integral),
 * capacidade, mensalidade (R$), ano letivo. Mostra vagas ocupadas/total quando o controller anexa
 * occupied. A IA só oferece turmas com vaga ao matricular.
 */
export default function EscolaClassesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EscolaClass | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['escola-classes'],
    queryFn: () => listClasses(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        grade: form.grade,
        shift: form.shift,
        capacity: Math.round(Number(form.capacity)),
        monthlyCents: Math.round(Number(form.monthly || '0') * 100),
        year: form.year.trim() === '' ? null : Math.round(Number(form.year)),
        description: form.description || null,
      }
      if (editing) return updateClass(editing.id, payload)
      return createClass(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['escola-classes'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar a turma.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (c: EscolaClass) => toggleClass(c.id, !c.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['escola-classes'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteClass(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['escola-classes'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'class_in_use') {
        alert('Esta turma tem matrículas — não pode ser excluída. Desative-a em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(c: EscolaClass) {
    setEditing(c)
    setForm({
      name: c.name,
      grade: c.grade,
      shift: c.shift,
      capacity: String(c.capacity),
      monthly: (c.monthlyCents / 100).toString(),
      year: c.year != null ? String(c.year) : '',
      description: c.description ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  // Onda 1 (backlog #1): fila de espera da turma (modal).
  const [waitlistClass, setWaitlistClass] = useState<EscolaClass | null>(null)
  const waitlist = useQuery({
    queryKey: ['escola-waitlist', waitlistClass?.id],
    queryFn: () => listWaitlist(waitlistClass!.id),
    enabled: waitlistClass !== null,
  })
  const notifyMutation = useMutation({
    mutationFn: (id: string) => notifyOpening(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['escola-waitlist'] }),
  })
  const waitStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'convertida' | 'desistiu' }) =>
      updateWaitlistStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['escola-waitlist'] }),
  })

  const items = data?.items ?? []

  function occupancyLabel(c: EscolaClass): string {
    if (c.occupied != null) return `${c.occupied}/${c.capacity} ocupadas`
    if (c.remainingSlots != null) return `${c.capacity - c.remainingSlots}/${c.capacity} ocupadas`
    return `${c.capacity} vagas`
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Turmas"
        description="Turmas por série e turno. A IA oferece turmas com vaga ao matricular."
        actions={<Button onClick={openCreate}>Nova turma</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as turmas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma turma cadastrada ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((c) => (
            <div key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.name}</span>
                  <Badge variant="info">{c.grade}</Badge>
                  <Badge variant="muted">{shiftLabel(c.shift)}</Badge>
                  {!c.active && <Badge variant="muted">inativa</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  {occupancyLabel(c)} · {formatBrl(c.monthlyCents)}/mês
                  {c.year ? ` · ${c.year}` : ''}
                  {c.description ? ` · ${c.description}` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={c.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(c)}
                  />
                  ativa
                </label>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  onClick={() => setWaitlistClass(c)}
                >
                  Fila de espera
                </Button>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(c)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(c.id)}
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
        title={editing ? 'Editar turma' : 'Nova turma'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
              <input
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                required
                maxLength={200}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Série</label>
              <input
                value={form.grade}
                onChange={(e) => setForm((f) => ({ ...f, grade: e.target.value }))}
                required
                maxLength={100}
                placeholder="Maternal II, Pré I…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Turno</label>
              <select
                value={form.shift}
                onChange={(e) => setForm((f) => ({ ...f, shift: e.target.value as EscolaShift }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                {SHIFTS.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Capacidade
              </label>
              <input
                type="number"
                min="1"
                max="200"
                value={form.capacity}
                required
                onChange={(e) => setForm((f) => ({ ...f, capacity: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Mensalidade (R$)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.monthly}
                required
                onChange={(e) => setForm((f) => ({ ...f, monthly: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Ano letivo
              </label>
              <input
                type="number"
                min="2000"
                max="2100"
                value={form.year}
                onChange={(e) => setForm((f) => ({ ...f, year: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Descrição
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
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

      <Modal
        open={waitlistClass !== null}
        onClose={() => setWaitlistClass(null)}
        title={`Fila de espera — ${waitlistClass?.name ?? ''}`}
        size="lg"
      >
        {waitlist.isPending ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (waitlist.data?.items ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Ninguém na fila desta turma. Quando a IA registrar interesse em turma cheia, a família
            aparece aqui.
          </p>
        ) : (
          <div className="divide-y divide-border rounded-lg border border-border">
            {(waitlist.data?.items ?? []).map((w) => (
              <div key={w.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <Badge variant="muted">#{w.position}</Badge>
                    <span className="truncate font-medium">{w.studentName}</span>
                    {w.status === 'avisada' && <Badge variant="warning">avisada</Badge>}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {w.contactName} · {w.contactPhone}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {w.status === 'aguardando' && (
                    <Button
                      className="h-7 px-2 text-xs"
                      disabled={notifyMutation.isPending}
                      onClick={() => notifyMutation.mutate(w.id)}
                    >
                      Avisar vaga
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={waitStatusMutation.isPending}
                    onClick={() => waitStatusMutation.mutate({ id: w.id, status: 'convertida' })}
                  >
                    Convertida
                  </Button>
                  <Button
                    variant="outline"
                    className="h-7 px-2 text-xs"
                    disabled={waitStatusMutation.isPending}
                    onClick={() => waitStatusMutation.mutate({ id: w.id, status: 'desistiu' })}
                  >
                    Desistiu
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  )
}
