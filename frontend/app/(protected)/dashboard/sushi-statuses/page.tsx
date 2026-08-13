'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import {
  createOrderStatus,
  deleteOrderStatus,
  listOrderStatuses,
  updateOrderStatus,
} from '@/lib/api/sushi/order-statuses'
import type { OrderStatusDef } from '@/profiles/sushi/sushi-types'

type FormState = {
  name: string
  sortOrder: string
  isInitial: boolean
  isTerminal: boolean
  notifyEnabled: boolean
  notifyText: string
  color: string
}
const EMPTY: FormState = {
  name: '',
  sortOrder: '0',
  isInitial: false,
  isTerminal: false,
  notifyEnabled: false,
  notifyText: '',
  color: '',
}

/**
 * Status de pedido + Notificações do SushiBot. O tenant gere a máquina de status e a mensagem de
 * WhatsApp disparada ao entrar em cada um. Exatamente UM status inicial (o backend reforça);
 * status inicial / em uso não pode ser excluído (409 surface). Listados por sortOrder.
 */
export default function SushiStatusesPage() {
  const qc = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<OrderStatusDef | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<OrderStatusDef | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['sushi-order-statuses'],
    queryFn: () => listOrderStatuses(),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        sortOrder: Number(form.sortOrder || '0'),
        isInitial: form.isInitial,
        isTerminal: form.isTerminal,
        notifyEnabled: form.notifyEnabled,
        notifyText: form.notifyText.trim() ? form.notifyText : null,
        color: form.color.trim() ? form.color : null,
      }
      if (editing) return updateOrderStatus(editing.id, payload)
      return createOrderStatus(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-order-statuses'] })
      qc.invalidateQueries({ queryKey: ['sushi-orders'] })
      setModalOpen(false)
      setEditing(null)
      setForm(EMPTY)
      setFormError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'duplicate_status') {
        setFormError('Já existe um status com esse nome.')
      } else {
        setFormError('Erro ao salvar o status.')
      }
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteOrderStatus(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sushi-order-statuses'] })
      qc.invalidateQueries({ queryKey: ['sushi-orders'] })
      setDeleteTarget(null)
      setDeleteError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'status_in_use') {
        setDeleteError('Há pedidos neste status — mova-os antes de excluir.')
      } else if (e instanceof ApiError && e.reason === 'initial_status_undeletable') {
        setDeleteError(
          'O status inicial não pode ser excluído. Defina outro como inicial primeiro.',
        )
      } else {
        setDeleteError('Erro ao excluir o status.')
      }
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setFormError(null)
    setModalOpen(true)
  }
  function openEdit(s: OrderStatusDef) {
    setEditing(s)
    setForm({
      name: s.name,
      sortOrder: String(s.sortOrder),
      isInitial: s.isInitial,
      isTerminal: s.isTerminal,
      notifyEnabled: s.notifyEnabled,
      notifyText: s.notifyText ?? '',
      color: s.color ?? '',
    })
    setFormError(null)
    setModalOpen(true)
  }

  const statuses = [...(data?.items ?? [])].sort((a, b) => a.sortOrder - b.sortOrder)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Status & Notificações"
        description="Defina o fluxo dos pedidos e a mensagem de WhatsApp enviada ao cliente em cada status."
        actions={<Button onClick={openCreate}>Novo status</Button>}
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os status.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : statuses.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum status cadastrado ainda.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {statuses.map((s) => (
            <div key={s.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground tabular-nums">
                  {s.sortOrder}
                </span>
                {s.color && (
                  <span
                    className="h-3 w-3 shrink-0 rounded-full border border-border"
                    style={{ backgroundColor: s.color }}
                  />
                )}
                <span className="truncate font-medium">{s.name}</span>
                {s.isInitial && <Badge variant="info">inicial</Badge>}
                {s.isTerminal && <Badge variant="muted">final</Badge>}
                {s.notifyEnabled && <Badge variant="success">notifica</Badge>}
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <Button variant="outline" className="h-7 px-2 text-xs" onClick={() => openEdit(s)}>
                  Editar
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  onClick={() => {
                    setDeleteError(null)
                    setDeleteTarget(s)
                  }}
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
        title={editing ? 'Editar status' : 'Novo status'}
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
                maxLength={120}
                placeholder="Recebido, Em preparo…"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Ordem</label>
              <input
                type="number"
                value={form.sortOrder}
                onChange={(e) => setForm((f) => ({ ...f, sortOrder: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
          </div>

          <div className="flex flex-wrap gap-4">
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={form.isInitial}
                onChange={(e) => setForm((f) => ({ ...f, isInitial: e.target.checked }))}
              />
              Status inicial
            </label>
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={form.isTerminal}
                onChange={(e) => setForm((f) => ({ ...f, isTerminal: e.target.checked }))}
              />
              Status final
            </label>
          </div>
          <p className="text-xs text-muted-foreground">
            Exatamente um status deve ser o inicial — ao marcar este, o anterior deixa de ser.
            Status finais (entregue, cancelado) saem do Kanban e vão para o histórico.
          </p>

          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Cor (opcional)
            </label>
            <input
              type="color"
              value={form.color || '#888888'}
              onChange={(e) => setForm((f) => ({ ...f, color: e.target.value }))}
              className="h-9 w-16 cursor-pointer rounded-md border border-border bg-background"
            />
            {form.color && (
              <button
                type="button"
                className="ml-2 text-xs text-muted-foreground underline"
                onClick={() => setForm((f) => ({ ...f, color: '' }))}
              >
                limpar
              </button>
            )}
          </div>

          <div className="space-y-3 rounded-lg border border-border p-3">
            <p className="text-xs font-semibold text-muted-foreground">
              Notificação ao entrar neste status
            </p>
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={form.notifyEnabled}
                onChange={(e) => setForm((f) => ({ ...f, notifyEnabled: e.target.checked }))}
              />
              Enviar mensagem de WhatsApp ao cliente
            </label>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Mensagem
              </label>
              <textarea
                value={form.notifyText}
                rows={3}
                disabled={!form.notifyEnabled}
                onChange={(e) => setForm((f) => ({ ...f, notifyText: e.target.value }))}
                placeholder="Seu pedido foi recebido e já está em preparo! 🍣"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm disabled:opacity-50"
              />
            </div>
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

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
            setDeleteError(null)
          }
        }}
        title="Excluir status?"
        description={deleteError ?? 'Esta ação não pode ser desfeita.'}
        confirmLabel="Excluir"
        loading={deleteMutation.isPending}
        onConfirm={() => {
          if (deleteTarget) deleteMutation.mutate(deleteTarget.id)
        }}
      />
    </div>
  )
}
