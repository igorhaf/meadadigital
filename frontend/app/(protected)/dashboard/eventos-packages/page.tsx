'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createPackage,
  deletePackage,
  listPackages,
  updatePackage,
} from '@/lib/api/eventos/packages'
import type { EventPackage } from '@/profiles/eventos/eventos-types'

type FormState = {
  name: string
  kind: 'pacote' | 'adicional'
  description: string
  price: string // R$
  suggestible: boolean
  active: boolean
}
const EMPTY: FormState = {
  name: '',
  kind: 'pacote',
  description: '',
  price: '',
  suggestible: false,
  active: true,
}

function formatBrl(cents: number): string {
  return `R$ ${(cents / 100).toFixed(2).replace('.', ',')}`
}

/**
 * Catálogo de pacotes e adicionais do buffet (onda Eventos 1, backlog #2). Autofill do editor de
 * orçamento (o item da proposta continua snapshot) e fonte do que a IA pode descrever/sugerir.
 */
export default function EventosPackagesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<EventPackage | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['eventos-packages'],
    queryFn: () => listPackages(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name.trim(),
        kind: form.kind,
        description: form.description.trim() || null,
        priceCents: Math.max(0, Math.round(Number(form.price || '0') * 100)),
        suggestible: form.suggestible,
        active: form.active,
      }
      if (editing) return updatePackage(editing.id, payload)
      return createPackage(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['eventos-packages'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o pacote.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (p: EventPackage) => updatePackage(p.id, { active: !p.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['eventos-packages'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePackage(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['eventos-packages'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: EventPackage) {
    setEditing(p)
    setForm({
      name: p.name,
      kind: p.kind,
      description: p.description ?? '',
      price: String(p.priceCents / 100),
      suggestible: p.suggestible,
      active: p.active,
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pacotes e adicionais"
        description="O catálogo que preenche o orçamento em segundos e diz à IA o que ela pode descrever — nunca inventar."
        actions={<Button onClick={openCreate}>Novo pacote</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o catálogo.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum pacote cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <Badge variant={p.kind === 'pacote' ? 'info' : 'muted'}>{p.kind}</Badge>
                <span className="truncate font-medium">{p.name}</span>
                {p.suggestible && <Badge variant="warning">sugerível pela IA</Badge>}
                {!p.active && <Badge variant="muted">inativo</Badge>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-sm tabular-nums">{formatBrl(p.priceCents)}</span>
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
        title={editing ? 'Editar pacote' : 'Novo pacote'}
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
                placeholder="Pacote Ouro, Open bar, Hora extra…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Tipo</label>
              <select
                value={form.kind}
                onChange={(e) =>
                  setForm((f) => ({ ...f, kind: e.target.value as FormState['kind'] }))
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
            <div className="flex items-end gap-4 pb-2">
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
              Descrição (a IA usa este texto)
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              rows={2}
              placeholder="O que está incluído…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.suggestible}
              className="mt-0.5"
              onChange={(e) => setForm((f) => ({ ...f, suggestible: e.target.checked }))}
            />
            <span>
              Sugerível pela IA (upsell consultivo)
              <span className="block text-xs text-muted-foreground">
                A IA pode mencionar este item quando combinar com o briefing — sem insistir, sem
                desconto.
              </span>
            </span>
          </label>
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
