'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import {
  createCoupon,
  deleteCoupon,
  listCoupons,
  toggleCoupon,
  updateCoupon,
} from '@/lib/api/comida/coupons'
import { formatBrl, type ComidaCoupon } from '@/profiles/comida/comida-types'

type FormState = {
  code: string
  kind: 'percent' | 'fixed'
  value: string // % ou R$
  minOrder: string // R$
  maxUses: string // vazio = ilimitado
  validUntil: string // yyyy-mm-dd ou vazio
  active: boolean
}
const EMPTY: FormState = { code: '', kind: 'percent', value: '', minOrder: '', maxUses: '', validUntil: '', active: true }

/** Formata o desconto de um cupom para exibição. */
function couponValueLabel(c: ComidaCoupon): string {
  return c.kind === 'percent' ? `${c.value}%` : formatBrl(c.value)
}

/**
 * Cupons de desconto do ComidaBot. CRUD via Modal. value é % (kind=percent) ou centavos (kind=fixed).
 * uses é somente leitura. maxUses/validUntil opcionais. duplicate_coupon / invalid_coupon do backend.
 */
export default function ComidaCouponsPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ComidaCoupon | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['comida-coupons'],
    queryFn: () => listCoupons(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const value =
        form.kind === 'percent'
          ? Math.round(Number(form.value || '0'))
          : Math.round(Number(form.value || '0') * 100)
      const payload = {
        code: form.code.trim().toUpperCase(),
        kind: form.kind,
        value,
        minOrderCents: Math.round(Number(form.minOrder || '0') * 100),
        maxUses: form.maxUses.trim() ? Number(form.maxUses) : null,
        validUntil: form.validUntil.trim() ? form.validUntil : null,
        active: form.active,
      }
      if (editing) return updateCoupon(editing.id, payload)
      return createCoupon(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['comida-coupons'] })
      setModalOpen(false); setEditing(null); setForm(EMPTY); setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_coupon') {
        setFormError('Já existe um cupom com esse código.')
      } else if (e instanceof ApiError && e.reason === 'invalid_coupon') {
        setFormError('Dados do cupom inválidos.')
      } else {
        setFormError('Erro ao salvar o cupom.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (c: ComidaCoupon) => toggleCoupon(c.id, !c.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comida-coupons'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCoupon(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comida-coupons'] }),
  })

  function openCreate() { setEditing(null); setForm(EMPTY); setFormError(null); setModalOpen(true) }
  function openEdit(c: ComidaCoupon) {
    setEditing(c)
    setForm({
      code: c.code,
      kind: c.kind,
      value: c.kind === 'percent' ? String(c.value) : String(c.value / 100),
      minOrder: c.minOrderCents ? String(c.minOrderCents / 100) : '',
      maxUses: c.maxUses !== null ? String(c.maxUses) : '',
      validUntil: c.validUntil ?? '',
      active: c.active,
    })
    setFormError(null); setModalOpen(true)
  }

  const coupons = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cupons"
        description="Cupons de desconto que a IA aplica quando o cliente informa o código."
        actions={<Button onClick={openCreate}>Novo cupom</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os cupons.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : coupons.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum cupom cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {coupons.map((c) => (
            <div key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate font-mono font-medium">{c.code}</span>
                <Badge variant="info">{couponValueLabel(c)}</Badge>
                {c.minOrderCents > 0 && (
                  <span className="text-xs text-muted-foreground">mín. {formatBrl(c.minOrderCents)}</span>
                )}
                {!c.active && <Badge variant="muted">inativo</Badge>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-xs text-muted-foreground tabular-nums">
                  {c.uses}{c.maxUses !== null ? `/${c.maxUses}` : ''} usos
                </span>
                {c.validUntil && (
                  <span className="text-xs text-muted-foreground">
                    até {new Date(c.validUntil).toLocaleDateString('pt-BR')}
                  </span>
                )}
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input type="checkbox" checked={c.active} disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(c)} />
                  ativo
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(c)}>Editar</Button>
                <Button variant="outline" className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate(c.id)}>Excluir</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Editar cupom' : 'Novo cupom'} size="md">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); saveMutation.mutate() }}>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Código</label>
              <input value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} required
                maxLength={40} placeholder="PRIMEIRAPEDIDO15"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm uppercase" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Tipo</label>
              <select value={form.kind}
                onChange={(e) => setForm((f) => ({ ...f, kind: e.target.value as 'percent' | 'fixed' }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm">
                <option value="percent">Percentual (%)</option>
                <option value="fixed">Valor fixo (R$)</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                {form.kind === 'percent' ? 'Desconto (%)' : 'Desconto (R$)'}
              </label>
              <input type="number" min="0" step={form.kind === 'percent' ? '1' : '0.01'} value={form.value} required
                onChange={(e) => setForm((f) => ({ ...f, value: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Pedido mínimo (R$)</label>
              <input type="number" min="0" step="0.01" value={form.minOrder}
                onChange={(e) => setForm((f) => ({ ...f, minOrder: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Máx. de usos (opcional)</label>
              <input type="number" min="1" step="1" value={form.maxUses} placeholder="ilimitado"
                onChange={(e) => setForm((f) => ({ ...f, maxUses: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Válido até (opcional)</label>
              <input type="date" value={form.validUntil}
                onChange={(e) => setForm((f) => ({ ...f, validUntil: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input type="checkbox" checked={form.active}
              onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
            Ativo
          </label>
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
