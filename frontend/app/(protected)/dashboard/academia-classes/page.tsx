'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createClass,
  deleteClass,
  listClasses,
  toggleClass,
  updateClass,
} from '@/lib/api/academia/classes'
import { dayOfWeekLabel, formatTime, type Class } from '@/profiles/academia/academia-types'

type FormState = {
  name: string
  modality: string
  dayOfWeek: string
  startTime: string
  durationMinutes: string
  capacity: string
  instructor: string
}
const EMPTY: FormState = { name: '', modality: '', dayOfWeek: '1', startTime: '07:00', durationMinutes: '60', capacity: '12', instructor: '' }

/** Agrupa aulas por dia da semana (0..6); a API já ordena por dia+hora. */
function groupByDay(items: Class[]): { dow: number; items: Class[] }[] {
  const groups: { dow: number; items: Class[] }[] = []
  for (const c of items) {
    const last = groups[groups.length - 1]
    if (last && last.dow === c.dayOfWeek) last.items.push(c)
    else groups.push({ dow: c.dayOfWeek, items: [c] })
  }
  return groups
}

/**
 * Aulas semanais do AcademiaBot (camada 7.7). Lista por dia da semana, mostrando horário + duração +
 * vagas ocupadas/capacidade. CRUD via Modal. A IA só oferece aulas com vaga.
 */
export default function AcademiaClassesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Class | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['academia-classes'],
    queryFn: () => listClasses(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        modality: form.modality,
        dayOfWeek: Number(form.dayOfWeek),
        startTime: form.startTime,
        durationMinutes: Math.round(Number(form.durationMinutes)),
        capacity: Math.round(Number(form.capacity)),
        instructor: form.instructor || null,
      }
      if (editing) return updateClass(editing.id, payload)
      return createClass(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-classes'] })
      setModalOpen(false); setEditing(null); setForm(EMPTY); setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar a aula.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (c: Class) => toggleClass(c.id, !c.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-classes'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteClass(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-classes'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'class_in_use') {
        alert('Esta aula tem matrículas — não pode ser excluída. Desative-a em vez disso.')
      }
    },
  })

  function openCreate() { setEditing(null); setForm(EMPTY); setFormError(null); setModalOpen(true) }
  function openEdit(c: Class) {
    setEditing(c)
    setForm({
      name: c.name,
      modality: c.modality,
      dayOfWeek: String(c.dayOfWeek),
      startTime: formatTime(c.startTime),
      durationMinutes: String(c.durationMinutes),
      capacity: String(c.capacity),
      instructor: c.instructor ?? '',
    })
    setFormError(null); setModalOpen(true)
  }

  const groups = groupByDay(data?.items ?? [])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Aulas"
        description="Aulas semanais recorrentes. A IA oferece aulas com vaga ao matricular."
        actions={<Button onClick={openCreate}>Nova aula</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as aulas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma aula cadastrada ainda.</p>
      ) : (
        <div className="space-y-8">
          {groups.map((g) => (
            <section key={g.dow} className="space-y-2">
              <h2 className="text-sm font-semibold text-muted-foreground">{dayOfWeekLabel(g.dow)}</h2>
              <div className="divide-y divide-border rounded-lg border border-border">
                {g.items.map((c) => (
                  <div key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{c.name}</span>
                        <Badge variant="muted">{c.modality}</Badge>
                        {!c.active && <Badge variant="muted">inativa</Badge>}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {formatTime(c.startTime)} · {c.durationMinutes}min · {c.capacity - c.remainingSlots}/{c.capacity} ocupadas
                        {c.instructor ? ` · ${c.instructor}` : ''}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                      <label className="flex items-center gap-1 text-xs text-muted-foreground">
                        <input type="checkbox" checked={c.active} disabled={toggleMutation.isPending}
                          onChange={() => toggleMutation.mutate(c)} />
                        ativa
                      </label>
                      <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(c)}>Editar</Button>
                      <Button variant="outline" className="h-7 px-2 text-xs"
                        disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate(c.id)}>Excluir</Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar aula' : 'Nova aula'} size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
              <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required
                maxLength={200} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Modalidade</label>
              <input value={form.modality} onChange={(e) => setForm((f) => ({ ...f, modality: e.target.value }))} required
                maxLength={100} placeholder="funcional, pilates, yoga…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Dia da semana</label>
              <select value={form.dayOfWeek} onChange={(e) => setForm((f) => ({ ...f, dayOfWeek: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                {[0, 1, 2, 3, 4, 5, 6].map((d) => <option key={d} value={d}>{dayOfWeekLabel(d)}</option>)}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Hora início</label>
              <input type="time" value={form.startTime} onChange={(e) => setForm((f) => ({ ...f, startTime: e.target.value }))} required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Duração (min)</label>
              <input type="number" min="15" max="240" step="15" value={form.durationMinutes} required
                onChange={(e) => setForm((f) => ({ ...f, durationMinutes: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Capacidade</label>
              <input type="number" min="1" max="100" value={form.capacity} required
                onChange={(e) => setForm((f) => ({ ...f, capacity: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Professor</label>
              <input value={form.instructor} onChange={(e) => setForm((f) => ({ ...f, instructor: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancelar</Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Salvando…' : editing ? 'Salvar' : 'Criar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
