'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createOption,
  createService,
  deleteOption,
  deleteService,
  listServices,
  toggleOption,
  toggleService,
  updateOption,
  updateService,
} from '@/lib/api/lavanderia/services'
import {
  LAVANDERIA_CATEGORIES,
  type LavanderiaCategoryId,
} from '@/profiles/lavanderia/lavanderia-categories'
import {
  formatBrl,
  type ServiceItem,
  type ServiceOption,
} from '@/profiles/lavanderia/lavanderia-types'

type FormState = {
  name: string
  description: string
  price: string // reais
  category: LavanderiaCategoryId
  turnaroundDays: string
  careInstructions: string
}

const EMPTY_FORM: FormState = {
  name: '',
  description: '',
  price: '',
  category: 'lavar',
  turnaroundDays: '1',
  careInstructions: '',
}

type OptionForm = { groupLabel: string; optionLabel: string; delta: string } // delta em reais
const EMPTY_OPTION: OptionForm = { groupLabel: '', optionLabel: '', delta: '0' }

/**
 * Catálogo de serviços do LavanderiaBot. Serviços agrupados por categoria, com prazo (turnaround_days)
 * e instruções de cuidado. Toggle de disponibilidade inline, criação/edição via Modal, busca por nome.
 * Cada serviço tem um modal secundário "Opções" (Acabamento/Cuidado). Preços salvos em centavos.
 */
export default function LavanderiaServicesPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ServiceItem | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  const [optionsItem, setOptionsItem] = useState<ServiceItem | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['lavanderia-services'],
    queryFn: () => listServices(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        description: form.description || null,
        priceCents: Math.round(Number(form.price) * 100),
        category: form.category,
        turnaroundDays: Math.max(0, Math.round(Number(form.turnaroundDays || 0))),
        careInstructions: form.careInstructions || null,
      }
      if (editing) return updateService(editing.id, payload)
      return createService(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['lavanderia-services'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_category') {
        setFormError('Categoria inválida.')
      } else {
        setFormError('Erro ao salvar o serviço.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (it: ServiceItem) => toggleService(it.id, !it.available),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lavanderia-services'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteService(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lavanderia-services'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'service_in_use') {
        alert('Este serviço está em pedidos — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setModalOpen(true)
  }

  function openEdit(it: ServiceItem) {
    setEditing(it)
    setForm({
      name: it.name,
      description: it.description ?? '',
      price: String(it.priceCents / 100),
      category: it.category,
      turnaroundDays: String(it.turnaroundDays),
      careInstructions: it.careInstructions ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = (data?.items ?? []).filter((it) =>
    it.name.toLowerCase().includes(search.trim().toLowerCase()),
  )

  const liveOptionsItem = optionsItem && (data?.items ?? []).find((it) => it.id === optionsItem.id)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Serviços"
        description="Serviços da sua lavanderia. A IA usa este catálogo (com prazos e opções) para atender os clientes."
        actions={<Button onClick={openCreate}>Novo serviço</Button>}
      />

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Buscar por nome…"
        className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm"
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o catálogo.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <div className="space-y-8">
          {LAVANDERIA_CATEGORIES.map((cat) => {
            const catItems = items.filter((it) => it.category === cat.id)
            return (
              <section key={cat.id} className="space-y-2">
                <h2 className="text-sm font-semibold text-muted-foreground">{cat.label}</h2>
                {catItems.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    Nenhum serviço nesta categoria ainda.
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
                            <Badge variant="muted">{it.turnaroundDays} dia(s)</Badge>
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
                          {it.careInstructions && (
                            <p className="truncate text-xs text-muted-foreground">
                              Cuidado: {it.careInstructions}
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

      {/* Modal: criar/editar serviço */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar serviço' : 'Novo serviço'}
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
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço/peça (R$)
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
                Prazo (dias)
              </label>
              <input
                type="number"
                min="0"
                step="1"
                value={form.turnaroundDays}
                required
                onChange={(e) => setForm((f) => ({ ...f, turnaroundDays: e.target.value }))}
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
                  setForm((f) => ({ ...f, category: e.target.value as LavanderiaCategoryId }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                {LAVANDERIA_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Instruções de cuidado (opcional)
            </label>
            <input
              value={form.careInstructions}
              onChange={(e) => setForm((f) => ({ ...f, careInstructions: e.target.value }))}
              placeholder="Ex.: lavagem a frio, não usar alvejante…"
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

      {/* Modal secundário: opções/modifiers do serviço */}
      <Modal
        open={optionsItem !== null}
        onClose={() => setOptionsItem(null)}
        title={liveOptionsItem ? `Opções — ${liveOptionsItem.name}` : 'Opções'}
        size="lg"
      >
        {liveOptionsItem ? (
          <OptionsEditor item={liveOptionsItem} />
        ) : (
          <p className="text-sm text-muted-foreground">Serviço não encontrado.</p>
        )}
      </Modal>
    </div>
  )
}

/** Editor de opções de um serviço: lista agrupada por groupLabel + form inline de adicionar/editar. */
function OptionsEditor({ item }: { item: ServiceItem }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<OptionForm>(EMPTY_OPTION)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['lavanderia-services'] })
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
    mutationFn: (op: ServiceOption) => toggleOption(item.id, op.id, !op.available),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: (optionId: string) => deleteOption(item.id, optionId),
    onSuccess: invalidate,
    onError: () => setError('Erro ao remover a opção.'),
  })

  function startEdit(op: ServiceOption) {
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

  const groups = new Map<string, ServiceOption[]>()
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
            maxLength={60}
            placeholder="Acabamento, Cuidado…"
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
            placeholder="Passar a vapor, Engomar…"
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
