'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createPlanner,
  deletePlanner,
  listPlanners,
  togglePlanner,
  updatePlanner,
} from '@/lib/api/eventos/planners'
import type { EventPlanner } from '@/profiles/eventos/eventos-types'

type FormState = { name: string; specialty: string; notes: string }
const EMPTY: FormState = { name: '', specialty: '', notes: '' }

/**
 * Cerimonialistas do EventosBot (camada 8.2). CRUD via Modal, toggle ativo, exclusão protegida (409
 * planner_in_use → orienta a desativar). Catálogo simples — sem agenda.
 */
export default function EventosPlannersPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EventPlanner | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['eventos-planners'],
    queryFn: () => listPlanners(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        return updatePlanner(editing.id, {
          name: form.name,
          specialty: form.specialty || null,
          notes: form.notes || null,
        })
      }
      return createPlanner({
        name: form.name,
        specialty: form.specialty || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-planners'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o cerimonialista.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (p: EventPlanner) => togglePlanner(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['eventos-planners'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePlanner(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['eventos-planners'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'planner_in_use') {
        alert('Este cerimonialista está atribuído a propostas — não pode ser excluído. Desative-o.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: EventPlanner) {
    setEditing(p)
    setForm({ name: p.name, specialty: p.specialty ?? '', notes: p.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cerimonialistas"
        description="Equipe responsável pelos eventos. Atribuir um cerimonialista a uma proposta é opcional."
        actions={<Button onClick={openCreate}>Novo cerimonialista</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os cerimonialistas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum cerimonialista cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  {!p.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {p.specialty && <p className="text-xs text-muted-foreground">{p.specialty}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={p.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(p)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(p.id)}
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
        title={editing ? 'Editar cerimonialista' : 'Novo cerimonialista'}
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
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
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Especialidade
            </label>
            <input
              value={form.specialty}
              onChange={(e) => setForm((f) => ({ ...f, specialty: e.target.value }))}
              placeholder="casamentos, corporativo, aniversários…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
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
