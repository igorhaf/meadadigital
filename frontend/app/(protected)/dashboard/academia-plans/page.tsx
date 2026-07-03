'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createPlan, deletePlan, listPlans, togglePlan, updatePlan } from '@/lib/api/academia/plans'
import { ApiError } from '@/lib/api/client'
import { formatPrice, type Plan } from '@/profiles/academia/academia-types'

type FormState = { name: string; price: string; description: string }
const EMPTY: FormState = { name: '', price: '', description: '' }

/**
 * Planos do AcademiaBot (camada 7.7). Lista com toggle ativo inline, CRUD via Modal. A IA oferece
 * os planos ativos ao matricular.
 */
export default function AcademiaPlansPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Plan | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['academia-plans'],
    queryFn: () => listPlans(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        monthlyCents: Math.round(Number(form.price || '0') * 100),
        description: form.description || null,
      }
      if (editing) return updatePlan(editing.id, payload)
      return createPlan(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['academia-plans'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar o plano.'),
  })

  const toggleMutation = useMutation({
    mutationFn: (p: Plan) => togglePlan(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-plans'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePlan(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['academia-plans'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'plan_in_use') {
        alert('Este plano tem matrículas — não pode ser excluído. Desative-o em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: Plan) {
    setEditing(p)
    setForm({ name: p.name, price: String(p.monthlyCents / 100), description: p.description ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const plans = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Planos"
        description="Planos mensais da academia. A IA oferece os planos ativos ao matricular."
        actions={<Button onClick={openCreate}>Novo plano</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os planos.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : plans.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum plano cadastrado ainda.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {plans.map((p) => (
            <div key={p.id} className="space-y-1 rounded-lg border border-border p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  {!p.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <span className="text-sm tabular-nums">{formatPrice(p.monthlyCents)}/mês</span>
              </div>
              {p.description && <p className="text-xs text-muted-foreground">{p.description}</p>}
              <div className="flex items-center gap-3 pt-1">
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
        title={editing ? 'Editar plano' : 'Novo plano'}
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
              placeholder="Mensal Livre, Mensal Aulas Coletivas…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Valor mensal (R$)
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
              Descrição
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
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
