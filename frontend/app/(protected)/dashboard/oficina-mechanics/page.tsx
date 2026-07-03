'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createMechanic,
  deleteMechanic,
  listMechanics,
  toggleMechanic,
  updateMechanic,
} from '@/lib/api/oficina/mechanics'
import type { OsMechanic } from '@/profiles/oficina/oficina-types'

type FormState = { name: string; specialty: string; notes: string }
const EMPTY: FormState = { name: '', specialty: '', notes: '' }

/**
 * Mecânicos do OficinaBot (camada 7.9). CRUD via Modal, toggle ativo, exclusão protegida (409
 * mechanic_in_use → orienta a desativar). Catálogo simples — sem agenda.
 */
export default function OficinaMechanicsPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<OsMechanic | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['oficina-mechanics'],
    queryFn: () => listMechanics(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        return updateMechanic(editing.id, {
          name: form.name,
          specialty: form.specialty || null,
          notes: form.notes || null,
        })
      }
      return createMechanic({
        name: form.name,
        specialty: form.specialty || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-mechanics'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o mecânico.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (m: OsMechanic) => toggleMechanic(m.id, !m.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['oficina-mechanics'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteMechanic(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['oficina-mechanics'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'mechanic_in_use') {
        alert(
          'Este mecânico está atribuído a ordens de serviço — não pode ser excluído. Desative-o.',
        )
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(m: OsMechanic) {
    setEditing(m)
    setForm({ name: m.name, specialty: m.specialty ?? '', notes: m.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Mecânicos"
        description="Equipe da oficina. Atribuir um mecânico a uma OS é opcional."
        actions={<Button onClick={openCreate}>Novo mecânico</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os mecânicos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum mecânico cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((m) => (
            <div key={m.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{m.name}</span>
                  {!m.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {m.specialty && <p className="text-xs text-muted-foreground">{m.specialty}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={m.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(m)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(m)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(m.id)}
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
        title={editing ? 'Editar mecânico' : 'Novo mecânico'}
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
              placeholder="motor/suspensão, elétrica/ar…"
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
