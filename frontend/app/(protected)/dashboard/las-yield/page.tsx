'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createYield, deleteYield, listYield, updateYield } from '@/lib/api/las/yield'
import type { YieldReference } from '@/profiles/las/las-types'

type FormState = {
  pieceType: string
  yarnSpec: string
  skeins: string
  notes: string
  active: boolean
}
const EMPTY: FormState = { pieceType: '', yarnSpec: '', skeins: '', notes: '', active: true }

/**
 * Referência de rendimento (onda Lãs 1, backlog #2): peça × fio → novelos ESTIMADOS. Alimenta a
 * calculadora da IA — que apresenta sempre como estimativa e, sem referência, diz que não tem.
 */
export default function LasYieldPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<YieldReference | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['las-yield'],
    queryFn: () => listYield(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        pieceType: form.pieceType.trim(),
        yarnSpec: form.yarnSpec.trim() || null,
        skeins: Math.min(200, Math.max(1, Math.round(Number(form.skeins) || 1))),
        notes: form.notes.trim() || null,
        active: form.active,
      }
      if (editing) return updateYield(editing.id, payload)
      return createYield(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['las-yield'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: () => setFormError('Erro ao salvar a referência.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteYield(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['las-yield'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(y: YieldReference) {
    setEditing(y)
    setForm({
      pieceType: y.pieceType,
      yarnSpec: y.yarnSpec ?? '',
      skeins: String(y.skeins),
      notes: y.notes ?? '',
      active: y.active,
    })
    setFormError(null)
    setModalOpen(true)
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Rendimento por peça"
        description="Quantos novelos cada peça leva, por tipo de fio. A IA usa como estimativa pra fechar o carrinho no tamanho certo — sem referência, ela não dimensiona."
        actions={<Button onClick={openCreate}>Nova referência</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as referências.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Nenhuma referência ainda — a IA responderá que não tem a estimativa.
        </p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((y) => (
            <div key={y.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate font-medium">{y.pieceType}</span>
                {y.yarnSpec && <Badge variant="info">{y.yarnSpec}</Badge>}
                {!y.active && <Badge variant="muted">inativa</Badge>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-sm tabular-nums">~{y.skeins} novelos</span>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(y)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(y.id)}
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
        title={editing ? 'Editar referência' : 'Nova referência'}
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
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Peça</label>
              <input
                value={form.pieceType}
                onChange={(e) => setForm((f) => ({ ...f, pieceType: e.target.value }))}
                required
                maxLength={120}
                placeholder="Cachecol, gorro, cobertor solteiro…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Fio (opcional)
              </label>
              <input
                value={form.yarnSpec}
                onChange={(e) => setForm((f) => ({ ...f, yarnSpec: e.target.value }))}
                placeholder="fio grosso, 100g/90m…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Novelos estimados
              </label>
              <input
                type="number"
                min={1}
                max={200}
                value={form.skeins}
                required
                onChange={(e) => setForm((f) => ({ ...f, skeins: e.target.value }))}
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
                Ativa
              </label>
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações (ex.: tamanho adulto)
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
