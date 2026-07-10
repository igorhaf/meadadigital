'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createCatalogItem,
  deleteCatalogItem,
  listCatalog,
  updateCatalogItem,
} from '@/lib/api/casamento/catalog'
import { ApiError } from '@/lib/api/client'
import { formatBrl, type WeddingCatalogItem } from '@/profiles/casamento/casamento-types'

type FormState = {
  name: string
  kind: 'pacote' | 'adicional'
  description: string
  price: string // R$
  active: boolean
}
const EMPTY: FormState = { name: '', kind: 'pacote', description: '', price: '', active: true }

/**
 * Catálogo de PACOTES e ADICIONAIS do CasamentoBot (onda 1, backlog #3). CRUD via Modal. É a fonte
 * do AUTOFILL do editor de orçamento (o item da proposta continua snapshot texto) e da apresentação
 * da IA (preço DO CATÁLOGO + upsell controlado de UM adicional). Inativo sai do autofill/IA.
 */
export default function CasamentoCatalogPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<WeddingCatalogItem | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['casamento-catalog'],
    queryFn: () => listCatalog(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name.trim(),
        kind: form.kind,
        description: form.description.trim() || null,
        priceCents: Math.round(Number(form.price || '0') * 100),
        active: form.active,
      }
      if (editing) {
        return updateCatalogItem(editing.id, {
          ...payload,
          clearDescription: payload.description === null,
        })
      }
      return createCatalogItem(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casamento-catalog'] })
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
    mutationFn: (item: WeddingCatalogItem) => updateCatalogItem(item.id, { active: !item.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['casamento-catalog'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCatalogItem(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['casamento-catalog'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(item: WeddingCatalogItem) {
    setEditing(item)
    setForm({
      name: item.name,
      kind: item.kind,
      description: item.description ?? '',
      price: String(item.priceCents / 100),
      active: item.active,
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Catálogo"
        description="Pacotes e adicionais com preço oficial — autofill do orçamento e vitrine que a IA apresenta aos noivos."
        actions={<Button onClick={openCreate}>Novo item</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o catálogo.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum pacote/adicional cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((item) => (
            <div key={item.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="truncate font-medium">{item.name}</span>
                  <Badge variant={item.kind === 'pacote' ? 'info' : 'default'}>
                    {item.kind === 'pacote' ? 'Pacote' : 'Adicional'}
                  </Badge>
                  {!item.active && <Badge variant="muted">inativo</Badge>}
                </div>
                {item.description && (
                  <p className="truncate text-xs text-muted-foreground">{item.description}</p>
                )}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-sm tabular-nums">{formatBrl(item.priceCents)}</span>
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
                placeholder="Pacote Ouro, Cabine de fotos, Brunch…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Tipo</label>
              <select
                value={form.kind}
                onChange={(e) =>
                  setForm((f) => ({ ...f, kind: e.target.value as 'pacote' | 'adicional' }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="pacote">Pacote</option>
                <option value="adicional">Adicional</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço (R$)
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
              Descrição (opcional)
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2}
              placeholder="O que está incluso"
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
