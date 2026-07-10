'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createCatalogItem,
  deleteCatalogItem,
  listCatalog,
  updateCatalogItem,
} from '@/lib/api/oficina/catalog'
import { formatPrice, type OficinaCatalogItem } from '@/profiles/oficina/oficina-types'

type FormState = {
  name: string
  category: string
  price: string // R$
  active: boolean
  notes: string
}
const EMPTY: FormState = { name: '', category: '', price: '', active: true, notes: '' }

/**
 * Catálogo de materiais/técnicas do OficinaBot (onda 2, backlog #15). CRUD via Modal. É a fonte do
 * AUTOFILL do editor de orçamento (o item da proposta continua snapshot texto) e do upsell da IA
 * (só nomes, sem preço — backlog #10). Inativo sai do autofill/upsell.
 */
export default function OficinaCatalogPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<OficinaCatalogItem | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['oficina-catalog'],
    queryFn: () => listCatalog(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name.trim(),
        category: form.category.trim() || null,
        unitPriceCents: Math.round(Number(form.price || '0') * 100),
        active: form.active,
        notes: form.notes.trim() || null,
      }
      if (editing) {
        return updateCatalogItem(editing.id, {
          ...payload,
          clearCategory: payload.category === null,
          clearNotes: payload.notes === null,
        })
      }
      return createCatalogItem(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-catalog'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_item')
        setFormError('Dados do item inválidos.')
      else setFormError('Erro ao salvar o item.')
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (item: OficinaCatalogItem) => updateCatalogItem(item.id, { active: !item.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['oficina-catalog'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCatalogItem(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['oficina-catalog'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(item: OficinaCatalogItem) {
    setEditing(item)
    setForm({
      name: item.name,
      category: item.category ?? '',
      price: String(item.unitPriceCents / 100),
      active: item.active,
      notes: item.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Materiais e técnicas"
        description="Catálogo que preenche o orçamento (autofill) e alimenta a sugestão da IA — sem valores na conversa."
        actions={<Button onClick={openCreate}>Novo item</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o catálogo.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum item cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((item) => (
            <div key={item.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate font-medium">{item.name}</span>
                {item.category && <Badge variant="info">{item.category}</Badge>}
                {!item.active && <Badge variant="muted">inativo</Badge>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-sm tabular-nums">{formatPrice(item.unitPriceCents)}</span>
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={item.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(item)}
                  />
                  ativo
                </label>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  onClick={() => openEdit(item)}
                >
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(item.id)}
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
        title={editing ? 'Editar item' : 'Novo item'}
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
                placeholder="Bordado à mão, forro de cetim, mão de obra…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria (opcional)
              </label>
              <input
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                placeholder="tecido, acabamento, mão de obra…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço unitário (R$)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.price}
                required
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div className="flex items-end pb-2">
              <label className="flex items-center gap-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
                />
                Ativo
              </label>
            </div>
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
