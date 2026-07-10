'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createTable,
  deleteTable,
  listTables,
  toggleTable,
  updateTable,
} from '@/lib/api/restaurant/tables'
import type { Table } from '@/profiles/restaurant/restaurant-types'

type FormState = {
  label: string
  capacity: string
  notes: string
}

const EMPTY_FORM: FormState = { label: '', capacity: '2', notes: '' }

/**
 * Mesas do MesaBot (camada 7.3). Lista com toggle de disponibilidade inline, criação/edição via
 * Modal. A IA usa as mesas disponíveis para atender pedidos de reserva.
 */
export default function TablesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Table | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['restaurant-tables'],
    queryFn: () => listTables(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        label: form.label,
        capacity: Math.round(Number(form.capacity)),
        notes: form.notes || null,
      }
      if (editing) return updateTable(editing.id, payload)
      return createTable(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['restaurant-tables'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'label_in_use') {
        setFormError('Já existe uma mesa com este nome.')
      } else {
        setFormError('Erro ao salvar a mesa.')
      }
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (t: Table) => toggleTable(t.id, !t.available),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['restaurant-tables'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteTable(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['restaurant-tables'] }),
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'table_in_use') {
        alert('Esta mesa tem reservas — não pode ser excluída. Desative-a em vez disso.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setModalOpen(true)
  }

  function openEdit(t: Table) {
    setEditing(t)
    setForm({ label: t.label, capacity: String(t.capacity), notes: t.notes ?? '' })
    setFormError(null)
    setModalOpen(true)
  }

  const tables = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Mesas"
        description="As mesas do seu restaurante. A IA oferece as mesas disponíveis ao marcar reservas."
        actions={<Button onClick={openCreate}>Nova mesa</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as mesas.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : tables.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Nenhuma mesa cadastrada ainda. Crie a primeira para a IA poder marcar reservas.
        </p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {tables.map((t) => (
            <div key={t.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{t.label}</span>
                  <Badge variant="muted">{t.capacity} lugares</Badge>
                  {!t.available && <Badge variant="muted">indisponível</Badge>}
                </div>
                {t.notes && <p className="truncate text-xs text-muted-foreground">{t.notes}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <label className="flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={t.available}
                    disabled={toggleMutation.isPending}
                    onChange={() => toggleMutation.mutate(t)}
                  />
                  disponível
                </label>
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(t)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(t.id)}
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
        title={editing ? 'Editar mesa' : 'Nova mesa'}
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
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Nome / identificador
            </label>
            <input
              value={form.label}
              onChange={(e) => setForm((f) => ({ ...f, label: e.target.value }))}
              required
              maxLength={60}
              placeholder="Mesa 1, Varanda 3…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Capacidade (lugares)
            </label>
            <input
              type="number"
              min="1"
              max="50"
              value={form.capacity}
              required
              onChange={(e) => setForm((f) => ({ ...f, capacity: e.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações (opcional)
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
