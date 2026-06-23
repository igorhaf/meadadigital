'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createMenuItem,
  deleteMenuItem,
  listMenu,
  toggleMenuItem,
  updateMenuItem,
} from '@/lib/api/sushi/menu'
import { SUSHI_CATEGORIES, type SushiCategoryId } from '@/profiles/sushi/sushi-categories'
import { formatBrl, type MenuItem } from '@/profiles/sushi/sushi-types'

type FormState = {
  name: string
  description: string
  price: string // reais
  category: SushiCategoryId
}

const EMPTY_FORM: FormState = { name: '', description: '', price: '', category: 'entradas' }

/**
 * Cardápio do SushiBot (camada 7.1). Itens agrupados por categoria, toggle de disponibilidade
 * inline, criação/edição via Modal, busca por nome. Preço em R$ (salvo em centavos).
 */
export default function MenuPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<MenuItem | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['sushi-menu'],
    queryFn: () => listMenu(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        description: form.description || null,
        priceCents: Math.round(Number(form.price) * 100),
        category: form.category,
      }
      if (editing) return updateMenuItem(editing.id, payload)
      return createMenuItem(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-menu'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_category') {
        setFormError('Categoria inválida.')
      } else {
        setFormError('Erro ao salvar o item.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (it: MenuItem) => toggleMenuItem(it.id, !it.available),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sushi-menu'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteMenuItem(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sushi-menu'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'menu_item_in_use') {
        alert('Este item está em pedidos — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setModalOpen(true)
  }

  function openEdit(it: MenuItem) {
    setEditing(it)
    setForm({
      name: it.name,
      description: it.description ?? '',
      price: String(it.priceCents / 100),
      category: it.category,
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = (data?.items ?? []).filter((it) =>
    it.name.toLowerCase().includes(search.trim().toLowerCase()),
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cardápio"
        description="Itens do seu restaurante. A IA usa este cardápio para atender os clientes."
        actions={<Button onClick={openCreate}>Novo item</Button>}
      />

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Buscar por nome…"
        className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o cardápio.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <div className="space-y-8">
          {SUSHI_CATEGORIES.map((cat) => {
            const catItems = items.filter((it) => it.category === cat.id)
            return (
              <section key={cat.id} className="space-y-2">
                <h2 className="text-sm font-semibold text-muted-foreground">{cat.label}</h2>
                {catItems.length === 0 ? (
                  <p className="text-xs text-muted-foreground">Nenhum item nesta categoria ainda.</p>
                ) : (
                  <div className="divide-y divide-border rounded-lg border border-border">
                    {catItems.map((it) => (
                      <div key={it.id} className="flex items-center justify-between gap-3 px-4 py-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-medium">{it.name}</span>
                            {!it.available && <Badge variant="muted">indisponível</Badge>}
                          </div>
                          {it.description && (
                            <p className="truncate text-xs text-muted-foreground">{it.description}</p>
                          )}
                        </div>
                        <div className="flex shrink-0 items-center gap-3">
                          <span className="tabular-nums">{formatBrl(it.priceCents)}</span>
                          <label className="flex items-center gap-1 text-xs text-muted-foreground">
                            <input
                              type="checkbox"
                              checked={it.available}
                              disabled={toggleMutation.isPending}
                              onChange={() => toggleMutation.mutate(it)}
                            />
                            disponível
                          </label>
                          <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(it)}>
                            Editar
                          </Button>
                          <Button
                            variant="outline"
                            className="h-7 px-2 text-xs"
                            disabled={deleteMutation.isPending}
                            onClick={() => deleteMutation.mutate(it.id)}
                          >
                            Excluir
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )
          })}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar item' : 'Novo item'} size="md">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
            <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required
              maxLength={120} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Descrição (opcional)</label>
            <textarea value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Preço (R$)</label>
              <input type="number" min="0" step="0.01" value={form.price} required
                onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Categoria</label>
              <select value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as SushiCategoryId }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                {SUSHI_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>{c.label}</option>
                ))}
              </select>
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
