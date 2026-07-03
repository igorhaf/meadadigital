'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { createZone, deleteZone, listZones, updateZone } from '@/lib/api/comida/zones'
import { formatBrl, type ComidaDeliveryZone } from '@/profiles/comida/comida-types'

type FormState = { name: string; fee: string; active: boolean }
const EMPTY: FormState = { name: '', fee: '', active: true }

/**
 * Zonas de entrega do ComidaBot (onda 1, backlog #8). A taxa FLAT das Configurações vira FALLBACK:
 * a IA pergunta o bairro, escolhe a zona e o pedido sai com a taxa da zona (snapshot do nome).
 */
export default function ComidaZonesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ComidaDeliveryZone | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['comida-zones'],
    queryFn: () => listZones(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name.trim(),
        feeCents: Math.round(Number(form.fee || '0') * 100),
        active: form.active,
      }
      if (editing) return updateZone(editing.id, payload)
      return createZone(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['comida-zones'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_zone')
        setFormError('Já existe uma zona com esse nome.')
      else if (e instanceof ApiError && e.reason === 'invalid_zone')
        setFormError('Dados da zona inválidos.')
      else setFormError('Erro ao salvar a zona.')
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (z: ComidaDeliveryZone) => updateZone(z.id, { active: !z.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comida-zones'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteZone(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comida-zones'] }),
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(z: ComidaDeliveryZone) {
    setEditing(z)
    setForm({ name: z.name, fee: String(z.feeCents / 100), active: z.active })
    setFormError(null)
    setModalOpen(true)
  }

  const zones = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Zonas de entrega"
        description="Taxa por bairro/zona — a taxa padrão das Configurações vale quando o bairro não casa com nenhuma zona."
        actions={<Button onClick={openCreate}>Nova zona</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as zonas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : zones.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Nenhuma zona cadastrada — todos os pedidos usam a taxa padrão.
        </p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {zones.map((z) => (
            <div key={z.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate font-medium">{z.name}</span>
                {!z.active && <Badge variant="muted">inativa</Badge>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-sm tabular-nums">{formatBrl(z.feeCents)}</span>
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={z.active}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(z)}
                  />
                  ativa
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(z)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(z.id)}
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
        title={editing ? 'Editar zona' : 'Nova zona'}
        size="sm"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Bairro / zona
            </label>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
              maxLength={120}
              placeholder="Centro, Zona Sul, Bairro Alto…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Taxa de entrega (R$)
            </label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={form.fee}
              required
              onChange={(e) => setForm((f) => ({ ...f, fee: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
            />
            Ativa
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
