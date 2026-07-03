'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createPackage,
  deletePackage,
  listPackages,
  togglePackage,
  updatePackage,
} from '@/lib/api/fotografia/packages'
import { formatBrl, type FotografiaPackage } from '@/profiles/fotografia/fotografia-types'

type FormState = {
  name: string
  category: string
  durationMinutes: number
  priceReais: string
  deliveryDays: number
  notes: string
}
const EMPTY: FormState = {
  name: '',
  category: '',
  durationMinutes: 60,
  priceReais: '',
  deliveryDays: 7,
  notes: '',
}

function reaisToCents(reais: string): number {
  const n = Number(reais.replace(',', '.'))
  return Number.isFinite(n) ? Math.round(n * 100) : 0
}

/**
 * Pacotes do estúdio (camada 8.16). Cada pacote tem categoria (texto livre), duração da sessão,
 * preço, prazo de entrega do material e estado ativo. CRUD via Modal, toggle ativo, exclusão
 * protegida (409 package_in_use). Rota /dashboard/fotografia-packages.
 */
export default function FotografiaPackagesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<FotografiaPackage | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['fotografia-packages'],
    queryFn: () => listPackages(),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const priceCents = reaisToCents(form.priceReais)
      if (editing) {
        return updatePackage(editing.id, {
          name: form.name,
          category: form.category || null,
          durationMinutes: form.durationMinutes,
          priceCents,
          deliveryDays: form.deliveryDays,
          notes: form.notes || null,
        })
      }
      return createPackage({
        name: form.name,
        category: form.category || null,
        durationMinutes: form.durationMinutes,
        priceCents,
        deliveryDays: form.deliveryDays,
        notes: form.notes || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fotografia-packages'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_duration') {
        setFormError('A duração precisa estar entre 5 e 480 minutos.')
      } else {
        setFormError('Erro ao salvar o pacote.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (p: FotografiaPackage) => togglePackage(p.id, !p.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['fotografia-packages'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePackage(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['fotografia-packages'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'package_in_use') {
        alert('Este pacote tem sessões — não pode ser excluído. Desative-o.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(p: FotografiaPackage) {
    setEditing(p)
    setForm({
      name: p.name,
      category: p.category ?? '',
      durationMinutes: p.durationMinutes,
      priceReais: (p.priceCents / 100).toFixed(2),
      deliveryDays: p.deliveryDays,
      notes: p.notes ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pacotes"
        description="As ofertas do estúdio. Cada pacote tem duração, preço e prazo de entrega do material."
        actions={<Button onClick={openCreate}>Novo pacote</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os pacotes.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum pacote cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{p.name}</span>
                  {p.category && <Badge variant="muted">{p.category}</Badge>}
                  <Badge variant="info">{p.durationMinutes} min</Badge>
                  <Badge variant="success">{formatBrl(p.priceCents)}</Badge>
                  {!p.active && <Badge variant="muted">inativo</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  Entrega em {p.deliveryDays} dia(s)
                  {p.notes ? ` · ${p.notes}` : ''}
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
        title={editing ? 'Editar pacote' : 'Novo pacote'}
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
                maxLength={120}
                placeholder="Ensaio gestante, Casamento, Book…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Categoria
              </label>
              <input
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                maxLength={120}
                placeholder="Ensaio, casamento, corporativo, vídeo…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Duração (minutos)
              </label>
              <input
                type="number"
                min={5}
                max={480}
                step={5}
                value={form.durationMinutes}
                required
                onChange={(e) =>
                  setForm((f) => ({ ...f, durationMinutes: Number(e.target.value) }))
                }
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Preço (R$)
              </label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.priceReais}
                required
                onChange={(e) => setForm((f) => ({ ...f, priceReais: e.target.value }))}
                placeholder="0,00"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Entrega (dias)
              </label>
              <input
                type="number"
                min={0}
                step={1}
                value={form.deliveryDays}
                required
                onChange={(e) => setForm((f) => ({ ...f, deliveryDays: Number(e.target.value) }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações internas
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
