'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createMenuItem,
  createOption,
  deleteMenuItem,
  deleteOption,
  listMenu,
  toggleMenuItem,
  toggleOption,
  updateMenuItem,
  updateOption,
} from '@/lib/api/pizzaria/menu'
import {
  PIZZARIA_CATEGORIES,
  type PizzariaCategoryId,
} from '@/profiles/pizzaria/pizzaria-categories'
import { formatBrl, type MenuItem, type MenuOption } from '@/profiles/pizzaria/pizzaria-types'

type FormState = {
  name: string
  description: string
  price: string // reais
  category: PizzariaCategoryId
}

const EMPTY_FORM: FormState = { name: '', description: '', price: '', category: 'pizzas_salgadas' }

type OptionForm = { groupLabel: string; optionLabel: string; delta: string } // delta em reais
const EMPTY_OPTION: OptionForm = { groupLabel: '', optionLabel: '', delta: '0' }

/**
 * Cardápio do PizzariaBot (delivery iFood-style + meio-a-meio). Itens agrupados por categoria,
 * toggle de disponibilidade inline, criação/edição via Modal, busca por nome. ESCAPADA 2: cada item
 * tem um modal secundário "Opções" (grupos como Tamanho/Borda) com adicionar/editar/remover/toggle —
 * o priceDeltaCents é exibido/editado em R$. Preços salvos em centavos. Os sabores de uma pizza
 * meio-a-meio são montados pela IA na conversa (regra do mais caro) — não há campo no cardápio.
 */
export default function PizzariaMenuPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<MenuItem | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  // Modal secundário de opções (escapada 2): aberto pelo botão "Opções" do card.
  const [optionsItem, setOptionsItem] = useState<MenuItem | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['pizzaria-menu'],
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
      qc.invalidateQueries({ queryKey: ['pizzaria-menu'] })
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
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pizzaria-menu'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteMenuItem(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pizzaria-menu'] }),
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

  // O modal de opções precisa do item SEMPRE fresco da query (após mutações de opção).
  const liveOptionsItem = optionsItem && (data?.items ?? []).find((it) => it.id === optionsItem.id)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cardápio"
        description="Itens da sua pizzaria. A IA usa este cardápio (com as opções) para atender os clientes e montar pizzas meio-a-meio."
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
          {PIZZARIA_CATEGORIES.map((cat) => {
            const catItems = items.filter((it) => it.category === cat.id)
            return (
              <section key={cat.id} className="space-y-2">
                <h2 className="text-sm font-semibold text-muted-foreground">{cat.label}</h2>
                {catItems.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    Nenhum item nesta categoria ainda.
                  </p>
                ) : (
                  <div className="divide-y divide-border rounded-lg border border-border">
                    {catItems.map((it) => (
                      <div
                        key={it.id}
                        className="flex items-center justify-between gap-3 px-4 py-3"
                      >
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-medium">{it.name}</span>
                            {!it.available && <Badge variant="muted">indisponível</Badge>}
                            {it.options.length > 0 && (
                              <Badge variant="info">{it.options.length} opç.</Badge>
                            )}
                          </div>
                          {it.description && (
                            <p className="truncate text-xs text-muted-foreground">
                              {it.description}
                            </p>
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
                          <Button
                            variant="outline"
                            className="h-7 px-2 text-xs"
                            onClick={() => setOptionsItem(it)}
                          >
                            Opções
                          </Button>
                          <Button
                            variant="outline"
                            className="h-7 px-2 text-xs"
                            onClick={() => openEdit(it)}
                          >
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

      {/* Modal: criar/editar item */}
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
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Nome</label>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
              maxLength={120}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Descrição (opcional)
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
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
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria
              </label>
              <select
                value={form.category}
                onChange={(e) =>
                  setForm((f) => ({ ...f, category: e.target.value as PizzariaCategoryId }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                {PIZZARIA_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
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

      {/* Modal secundário: opções/modifiers do item (ESCAPADA 2) */}
      <Modal
        open={optionsItem !== null}
        onClose={() => setOptionsItem(null)}
        title={liveOptionsItem ? `Opções — ${liveOptionsItem.name}` : 'Opções'}
        size="lg"
      >
        {liveOptionsItem ? (
          <OptionsEditor item={liveOptionsItem} />
        ) : (
          <p className="text-sm text-muted-foreground">Item não encontrado.</p>
        )}
      </Modal>
    </div>
  )
}

/** Editor de opções de um item: lista agrupada por groupLabel + form inline de adicionar/editar. */
function OptionsEditor({ item }: { item: MenuItem }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<OptionForm>(EMPTY_OPTION)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['pizzaria-menu'] })
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        groupLabel: form.groupLabel,
        optionLabel: form.optionLabel,
        priceDeltaCents: Math.round(Number(form.delta || 0) * 100),
      }
      if (editingId) return updateOption(item.id, editingId, payload)
      return createOption(item.id, payload)
    },
    onSuccess: () => {
      invalidate()
      setForm(EMPTY_OPTION)
      setEditingId(null)
      setError(null)
    },
    onError: () => setError('Erro ao salvar a opção.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (op: MenuOption) => toggleOption(item.id, op.id, !op.available),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: (optionId: string) => deleteOption(item.id, optionId),
    onSuccess: invalidate,
    onError: () => setError('Erro ao remover a opção.'),
  })

  function startEdit(op: MenuOption) {
    setEditingId(op.id)
    setForm({
      groupLabel: op.groupLabel,
      optionLabel: op.optionLabel,
      delta: String(op.priceDeltaCents / 100),
    })
    setError(null)
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_OPTION)
    setError(null)
  }

  // Agrupa por groupLabel preservando a ordem (sortOrder dentro do grupo já vem do backend).
  const groups = new Map<string, MenuOption[]>()
  for (const op of item.options) {
    const arr = groups.get(op.groupLabel) ?? []
    arr.push(op)
    groups.set(op.groupLabel, arr)
  }

  return (
    <div className="space-y-4">
      {item.options.length === 0 ? (
        <p className="text-xs text-muted-foreground">Nenhuma opção cadastrada ainda.</p>
      ) : (
        <div className="space-y-4">
          {[...groups.entries()].map(([group, ops]) => (
            <section key={group} className="space-y-2">
              <h3 className="text-sm font-semibold text-muted-foreground">{group}</h3>
              <div className="divide-y divide-border rounded-lg border border-border">
                {ops.map((op) => (
                  <div
                    key={op.id}
                    className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                  >
                    <div className="min-w-0">
                      <span className="font-medium">{op.optionLabel}</span>
                      {!op.available && (
                        <Badge variant="muted" className="ml-2">
                          indisponível
                        </Badge>
                      )}
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className="text-xs text-muted-foreground tabular-nums">
                        {op.priceDeltaCents >= 0 ? '+' : ''}
                        {formatBrl(op.priceDeltaCents)}
                      </span>
                      <label className="flex items-center gap-1 text-xs text-muted-foreground">
                        <input
                          type="checkbox"
                          checked={op.available}
                          disabled={toggleMutation.isPending}
                          onChange={() => toggleMutation.mutate(op)}
                        />
                        disp.
                      </label>
                      <Button
                        variant="outline"
                        className="h-6 px-2 text-xs"
                        onClick={() => startEdit(op)}
                      >
                        Editar
                      </Button>
                      <Button
                        variant="outline"
                        className="h-6 px-2 text-xs"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(op.id)}
                      >
                        Remover
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      <form
        className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
        onSubmit={(e) => {
          e.preventDefault()
          saveMutation.mutate()
        }}
      >
        <div className="min-w-[8rem] flex-1">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Grupo</label>
          <input
            value={form.groupLabel}
            onChange={(e) => setForm((f) => ({ ...f, groupLabel: e.target.value }))}
            required
            maxLength={80}
            placeholder="Tamanho, Borda…"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
        </div>
        <div className="min-w-[8rem] flex-1">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Opção</label>
          <input
            value={form.optionLabel}
            onChange={(e) => setForm((f) => ({ ...f, optionLabel: e.target.value }))}
            required
            maxLength={80}
            placeholder="Grande, Borda recheada…"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
        </div>
        <div className="w-28">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">
            + R$ (delta)
          </label>
          <input
            type="number"
            step="0.01"
            value={form.delta}
            onChange={(e) => setForm((f) => ({ ...f, delta: e.target.value }))}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
          />
        </div>
        <Button type="submit" className="h-8 px-3 text-xs" disabled={saveMutation.isPending}>
          {saveMutation.isPending ? 'Salvando…' : editingId ? 'Salvar' : 'Adicionar'}
        </Button>
        {editingId && (
          <Button type="button" variant="outline" className="h-8 px-3 text-xs" onClick={cancelEdit}>
            Cancelar edição
          </Button>
        )}
      </form>
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
