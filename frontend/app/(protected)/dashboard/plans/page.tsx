'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { Modal } from '@/components/ui/modal'
import { createPlan, deletePlan, listPlans, updatePlan, type Plan } from '@/lib/api/admin/plans'
import { ApiError } from '@/lib/api/client'

/** Formata centavos em R$ pt-BR; 0 vira "Sob consulta". */
function formatPrice(cents: number): string {
  if (cents === 0) return 'Sob consulta'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** Limite null = ilimitado (∞). */
function limit(v: number | null): string {
  return v === null ? '∞' : String(v)
}

type FormState = {
  name: string
  slug: string
  price: string // em reais (string do input)
  maxAdmins: string
  maxFaqs: string
  maxConversationsMonth: string
  maxUsers: string
  features: string // jsonb raw
}

const EMPTY_FORM: FormState = {
  name: '',
  slug: '',
  price: '',
  maxAdmins: '',
  maxFaqs: '',
  maxConversationsMonth: '',
  maxUsers: '',
  features: '',
}

/** "" → null (ilimitado); número válido → int. */
function toLimit(s: string): number | null {
  if (s.trim() === '') return null
  const n = Number(s)
  return Number.isFinite(n) ? Math.trunc(n) : null
}

/**
 * CRUD de planos (camada 6.8, super-admin). Lista, criação/edição via Modal, toggle ativo,
 * soft delete. NÃO integra com empresas (companies.plan_id é fase futura).
 */
export default function PlansPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Plan | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-plans'],
    queryFn: listPlans,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      let features: Record<string, unknown> | null = null
      if (form.features.trim()) {
        features = JSON.parse(form.features) as Record<string, unknown>
      }
      const payload = {
        name: form.name,
        slug: form.slug,
        monthlyPriceCents: form.price.trim() ? Math.round(Number(form.price) * 100) : 0,
        maxAdmins: toLimit(form.maxAdmins),
        maxFaqs: toLimit(form.maxFaqs),
        maxConversationsMonth: toLimit(form.maxConversationsMonth),
        maxUsers: toLimit(form.maxUsers),
        features,
      }
      if (editing) return updatePlan(editing.id, payload)
      return createPlan(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-plans'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'plan_slug_exists') {
        setFormError('Já existe um plano com esse slug.')
      } else if (e instanceof SyntaxError) {
        setFormError('Features não é um JSON válido.')
      } else {
        setFormError('Erro ao salvar o plano.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (p: Plan) => updatePlan(p.id, { active: !p.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-plans'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePlan(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-plans'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setModalOpen(true)
  }

  function openEdit(p: Plan) {
    setEditing(p)
    setForm({
      name: p.name,
      slug: p.slug,
      price: p.monthlyPriceCents ? String(p.monthlyPriceCents / 100) : '',
      maxAdmins: p.maxAdmins?.toString() ?? '',
      maxFaqs: p.maxFaqs?.toString() ?? '',
      maxConversationsMonth: p.maxConversationsMonth?.toString() ?? '',
      maxUsers: p.maxUsers?.toString() ?? '',
      features:
        p.features && Object.keys(p.features).length > 0 ? JSON.stringify(p.features, null, 2) : '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const columns: Column<Plan>[] = [
    { key: 'name', header: 'Nome', render: (p) => <span className="font-medium">{p.name}</span> },
    {
      key: 'slug',
      header: 'Slug',
      render: (p) => <span className="font-mono text-xs text-muted-foreground">{p.slug}</span>,
    },
    {
      key: 'price',
      header: 'Preço/mês',
      render: (p) => <span className="tabular-nums">{formatPrice(p.monthlyPriceCents)}</span>,
    },
    {
      key: 'limits',
      header: 'Limites (adm/faqs/conv)',
      render: (p) => (
        <span className="text-xs text-muted-foreground tabular-nums">
          {limit(p.maxAdmins)} / {limit(p.maxFaqs)} / {limit(p.maxConversationsMonth)}
        </span>
      ),
    },
    {
      key: 'active',
      header: 'Estado',
      render: (p) =>
        p.active ? <Badge variant="success">Ativo</Badge> : <Badge variant="muted">Inativo</Badge>,
    },
    {
      key: 'actions',
      header: '',
      render: (p) => (
        <div className="flex justify-end gap-1">
          <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(p)}>
            Editar
          </Button>
          <Button
            variant="outline"
            className="h-7 px-2 text-xs"
            disabled={toggleMutation.isPending}
            onClick={() => toggleMutation.mutate(p)}
          >
            {p.active ? 'Desativar' : 'Ativar'}
          </Button>
          {p.active && (
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(p.id)}
            >
              Excluir
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Planos"
        description="Catálogo de planos e limites. (Integração com empresas é fase futura.)"
        actions={<Button onClick={openCreate}>Novo plano</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os planos.</p>
      ) : (
        <DataTable<Plan>
          data={data?.items ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum plano cadastrado."
        />
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar plano' : 'Novo plano'}
        size="lg"
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
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Slug</label>
              <input
                value={form.slug}
                onChange={(e) => setForm((f) => ({ ...f, slug: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Preço mensal (R$, 0 = sob consulta)
            </label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={form.price}
              onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Máx. admins (vazio = ∞)
              </label>
              <input
                type="number"
                min="0"
                value={form.maxAdmins}
                onChange={(e) => setForm((f) => ({ ...f, maxAdmins: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Máx. FAQs (vazio = ∞)
              </label>
              <input
                type="number"
                min="0"
                value={form.maxFaqs}
                onChange={(e) => setForm((f) => ({ ...f, maxFaqs: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Máx. conversas/mês (vazio = ∞)
              </label>
              <input
                type="number"
                min="0"
                value={form.maxConversationsMonth}
                onChange={(e) => setForm((f) => ({ ...f, maxConversationsMonth: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Máx. usuários (vazio = ∞)
              </label>
              <input
                type="number"
                min="0"
                value={form.maxUsers}
                onChange={(e) => setForm((f) => ({ ...f, maxUsers: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Features (JSON, opcional)
            </label>
            <textarea
              value={form.features}
              onChange={(e) => setForm((f) => ({ ...f, features: e.target.value }))}
              rows={3}
              placeholder='{"webhooks": true}'
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm"
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
