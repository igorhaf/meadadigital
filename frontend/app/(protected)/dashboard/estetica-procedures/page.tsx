'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createProcedure,
  deleteProcedure,
  listProcedures,
  toggleProcedure,
  updateProcedure,
} from '@/lib/api/estetica/procedures'
import { formatPrice, type AestheticProcedure } from '@/profiles/estetica/estetica-types'

type FormState = {
  name: string
  category: string
  durationMinutes: string
  price: string
  notes: string
}
const EMPTY: FormState = { name: '', category: '', durationMinutes: '60', price: '', notes: '' }

/** Procedimentos do EsteticaBot (camada 8.3). Duração + preço POR SESSÃO (base do total do pacote). */
export default function EsteticaProceduresPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AestheticProcedure | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['estetica-procedures'],
    queryFn: () => listProcedures(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        name: form.name,
        category: form.category || null,
        durationMinutes: Math.max(15, Math.round(Number(form.durationMinutes) || 60)),
        unitPriceCents: Math.round(Number(form.price || 0) * 100),
        notes: form.notes || null,
      }
      return editing ? updateProcedure(editing.id, payload) : createProcedure(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['estetica-procedures'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o procedimento.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (p: AestheticProcedure) => toggleProcedure(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['estetica-procedures'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteProcedure(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['estetica-procedures'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'procedure_in_use') {
        alert(
          'Este procedimento está em agendamentos ou pacotes — não pode ser excluído. Desative-o.',
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
  function openEdit(p: AestheticProcedure) {
    setEditing(p)
    setForm({
      name: p.name,
      category: p.category ?? '',
      durationMinutes: String(p.durationMinutes),
      price: (p.unitPriceCents / 100).toFixed(2),
      notes: p.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Procedimentos"
        description="Catálogo de procedimentos. O preço é por sessão — o pacote multiplica pelas sessões."
        actions={<Button onClick={openCreate}>Novo procedimento</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os procedimentos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum procedimento cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  {p.category && <Badge variant="muted">{p.category}</Badge>}
                  {!p.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  {p.durationMinutes}min · {formatPrice(p.unitPriceCents)}/sessão
                </p>
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
        title={editing ? 'Editar procedimento' : 'Novo procedimento'}
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
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria
              </label>
              <input
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                placeholder="facial…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Duração (min)
              </label>
              <input
                type="number"
                min="15"
                value={form.durationMinutes}
                required
                onChange={(e) => setForm((f) => ({ ...f, durationMinutes: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço/sessão (R$)
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
