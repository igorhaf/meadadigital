'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createArtisan,
  deleteArtisan,
  listArtisans,
  toggleArtisan,
  updateArtisan,
} from '@/lib/api/atelie/artisans'
import { ApiError } from '@/lib/api/client'
import type { AtelieArtisan } from '@/profiles/atelie/atelie-types'

type FormState = { name: string; specialty: string; notes: string }
const EMPTY: FormState = { name: '', specialty: '', notes: '' }

/**
 * Artesãos do AtelieBot (camada 8.14). CRUD via Modal, toggle ativo, exclusão protegida (409
 * artisan_in_use → orienta a desativar). Catálogo simples — sem agenda.
 */
export default function AtelieArtisansPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AtelieArtisan | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['atelie-artisans'],
    queryFn: () => listArtisans(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editing) {
        return updateArtisan(editing.id, {
          name: form.name,
          specialty: form.specialty || null,
          notes: form.notes || null,
        })
      }
      return createArtisan({
        name: form.name,
        specialty: form.specialty || null,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['atelie-artisans'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o artesão.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (a: AtelieArtisan) => toggleArtisan(a.id, !a.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['atelie-artisans'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteArtisan(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['atelie-artisans'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'artisan_in_use') {
        alert('Este artesão está atribuído a propostas — não pode ser excluído. Desative-o.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(a: AtelieArtisan) {
    setEditing(a)
    setForm({ name: a.name, specialty: a.specialty ?? '', notes: a.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Artesãos"
        description="Equipe responsável pelos projetos. Atribuir um artesão a uma proposta é opcional."
        actions={<Button onClick={openCreate}>Novo artesão</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os artesãos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum artesão cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((a) => (
            <div key={a.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{a.name}</span>
                  {!a.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {a.specialty && <p className="text-xs text-muted-foreground">{a.specialty}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={a.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(a)}
                  />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(a)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(a.id)}
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
        title={editing ? 'Editar artesão' : 'Novo artesão'}
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
              placeholder="costura, bordado, modelagem, ilustração…"
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
