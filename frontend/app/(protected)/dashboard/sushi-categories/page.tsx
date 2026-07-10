'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createCategory,
  deleteCategory,
  listCategories,
  toggleCategory,
  updateCategory,
} from '@/lib/api/sushi/categories'
import type { Category } from '@/profiles/sushi/sushi-types'

type FormState = { name: string; sortOrder: string; active: boolean }
const EMPTY: FormState = { name: '', sortOrder: '0', active: true }

/**
 * Categorias do cardápio do SushiBot. Geridas pelo tenant (não mais hardcoded). Listadas por
 * sortOrder, CRUD via Modal, toggle ativo inline, exclusão com confirmação. category_in_use é
 * bloqueado pelo backend (categoria com itens no cardápio).
 */
export default function SushiCategoriesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['sushi-categories'],
    queryFn: () => listCategories(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        sortOrder: Number(form.sortOrder || '0'),
        active: form.active,
      }
      if (editing) return updateCategory(editing.id, payload)
      return createCategory(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-categories'] })
      qc.invalidateQueries({ queryKey: ['sushi-menu'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_category') {
        setFormError('Já existe uma categoria com esse nome.')
      } else {
        setFormError('Erro ao salvar a categoria.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (c: Category) => toggleCategory(c.id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sushi-categories'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCategory(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-categories'] })
      qc.invalidateQueries({ queryKey: ['sushi-menu'] })
      setDeleteTarget(null)
      setDeleteError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'category_in_use') {
        setDeleteError('Esta categoria tem itens no cardápio — mova ou exclua os itens antes.')
      } else {
        setDeleteError('Erro ao excluir a categoria.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(c: Category) {
    setEditing(c)
    setForm({ name: c.name, sortOrder: String(c.sortOrder), active: c.active })
    setFormError(null)
    setModalOpen(true)
  }

  const categories = [...(data?.items ?? [])].sort((a, b) => a.sortOrder - b.sortOrder)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Categorias"
        description="Organize seu cardápio em categorias. A IA usa a ordem definida aqui."
        actions={<Button onClick={openCreate}>Nova categoria</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as categorias.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : categories.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma categoria cadastrada ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {categories.map((c) => (
            <div key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground tabular-nums">
                  {c.sortOrder}
                </span>
                <span className="truncate font-medium">{c.name}</span>
                {!c.active && <Badge variant="muted">inativa</Badge>}
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
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(c)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  onClick={() => {
                    setDeleteError(null)
                    setDeleteTarget(c)
                  }}
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
        title={editing ? 'Editar categoria' : 'Nova categoria'}
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
              maxLength={120}
              placeholder="Hot rolls, Sashimi, Bebidas…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Ordem</label>
              <input
                type="number"
                value={form.sortOrder}
                onChange={(e) => setForm((f) => ({ ...f, sortOrder: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <label className="flex items-end gap-2 pb-2 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
              />
              Ativa
            </label>
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

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
            setDeleteError(null)
          }
        }}
        title="Excluir categoria?"
        description={deleteError ?? 'Esta ação não pode ser desfeita.'}
        confirmLabel="Excluir"
        loading={deleteMutation.isPending}
        onConfirm={() => {
          if (deleteTarget) deleteMutation.mutate(deleteTarget.id)
        }}
      />
    </div>
  )
}
