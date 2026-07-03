'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { listMechanics } from '@/lib/api/oficina/mechanics'
import {
  addItem,
  deleteItem,
  getOrder,
  listOrders,
  openOrder,
  updateOrderStatus,
} from '@/lib/api/oficina/orders'
import { listVehicles } from '@/lib/api/oficina/vehicles'
import {
  formatDate,
  formatPrice,
  kindLabel,
  type OsItemKind,
  type ServiceOrder,
} from '@/profiles/oficina/oficina-types'
import {
  ALLOWED_NEXT,
  ITEMS_LOCKED,
  OS_STATUSES,
  statusLabel,
  type OsStatusId,
} from '@/profiles/oficina/os-status'

function StatusBadge({ status }: { status: OsStatusId }) {
  const variant =
    status === 'aprovada' || status === 'concluida'
      ? 'success'
      : status === 'entregue'
        ? 'info'
        : status === 'orcada'
          ? 'warning'
          : status === 'recusada' || status === 'cancelada'
            ? 'muted'
            : 'default'
  return <Badge variant={variant}>{statusLabel(status)}</Badge>
}

type OpenForm = { vehicleId: string; mechanicId: string; complaint: string; notes: string }
const EMPTY_OPEN: OpenForm = { vehicleId: '', mechanicId: '', complaint: '', notes: '' }

type ItemForm = { kind: OsItemKind; description: string; quantity: string; price: string }
const EMPTY_ITEM: ItemForm = { kind: 'peca', description: '', quantity: '1', price: '' }

/**
 * Ordens de serviço do OficinaBot (camada 7.9). Lista por status, abre OS (Modal), detalhe com
 * EDITOR DE ITENS inline (total recalculado pelo backend a cada mutação) e botões de transição de
 * status (ALLOWED_NEXT). Orçar exige ≥1 item (400 empty_budget). Em estados travados o editor some
 * (409 order_locked defensivo).
 */
export default function OficinaOrdersPage() {
  const qc = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const [page, setPage] = useState(0)

  const [openModal, setOpenModal] = useState(false)
  const [openForm, setOpenForm] = useState<OpenForm>(EMPTY_OPEN)
  const [openError, setOpenError] = useState<string | null>(null)

  const [detailId, setDetailId] = useState<string | null>(null)
  const [itemForm, setItemForm] = useState<ItemForm>(EMPTY_ITEM)
  const [itemError, setItemError] = useState<string | null>(null)
  const [statusTarget, setStatusTarget] = useState<OsStatusId | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  const { data, isPending, isError } = useQuery({
    queryKey: ['oficina-orders', status, page],
    queryFn: () => listOrders({ status: status || undefined, page, pageSize: 50 }),
    placeholderData: keepPreviousData,
  })

  const vehicles = useQuery({
    queryKey: ['oficina-vehicles-all'],
    queryFn: () => listVehicles({ active: true }),
  })
  const mechanics = useQuery({
    queryKey: ['oficina-mechanics-all'],
    queryFn: () => listMechanics({ onlyActive: true }),
  })

  const detail = useQuery({
    queryKey: ['oficina-order', detailId],
    queryFn: () => getOrder(detailId as string),
    enabled: detailId !== null,
  })

  const openMutation = useMutation({
    mutationFn: () =>
      openOrder({
        vehicleId: openForm.vehicleId,
        mechanicId: openForm.mechanicId || null,
        complaint: openForm.complaint,
        notes: openForm.notes || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-orders'] })
      setOpenModal(false)
      setOpenForm(EMPTY_OPEN)
      setOpenError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'inactive_vehicle')
        setOpenError('Esse veículo está arquivado.')
      else if (e instanceof ApiError && e.reason === 'inactive_mechanic')
        setOpenError('Esse mecânico está inativo.')
      else setOpenError('Erro ao abrir a ordem de serviço.')
    },
  })

  const addItemMutation = useMutation({
    mutationFn: () => {
      if (!detailId) throw new Error('sem OS')
      return addItem(detailId, {
        kind: itemForm.kind,
        description: itemForm.description,
        quantity: Math.max(1, Math.round(Number(itemForm.quantity) || 1)),
        unitPriceCents: Math.round(Number(itemForm.price || 0) * 100),
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-order', detailId] })
      qc.invalidateQueries({ queryKey: ['oficina-orders'] })
      setItemForm(EMPTY_ITEM)
      setItemError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'order_locked')
        setItemError('Esta OS não aceita mais alteração de itens.')
      else setItemError('Erro ao adicionar o item.')
    },
  })

  const deleteItemMutation = useMutation({
    mutationFn: (itemId: string) => {
      if (!detailId) throw new Error('sem OS')
      return deleteItem(detailId, itemId)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-order', detailId] })
      qc.invalidateQueries({ queryKey: ['oficina-orders'] })
    },
  })

  const statusMutation = useMutation({
    mutationFn: (newStatus: OsStatusId) => {
      if (!detailId) throw new Error('sem OS')
      return updateOrderStatus(detailId, newStatus)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['oficina-order', detailId] })
      qc.invalidateQueries({ queryKey: ['oficina-orders'] })
      setStatusTarget(null)
      setStatusError(null)
    },
    onError: (e) => {
      setStatusTarget(null)
      if (e instanceof ApiError && e.reason === 'empty_budget')
        setStatusError('Adicione ao menos um item antes de orçar.')
      else if (e instanceof ApiError && e.reason === 'invalid_status_transition')
        setStatusError('Transição de status inválida.')
      else setStatusError('Erro ao mudar o status.')
    },
  })

  const items = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 50))
  const o = detail.data
  const itemsLocked = o ? ITEMS_LOCKED[o.status] : true

  return (
    <div className="space-y-6">
      <PageHeader
        title="Ordens de serviço"
        description="A IA abre OS pelo WhatsApp; o mecânico orça aqui e o cliente aprova pela conversa."
        actions={
          <Button
            onClick={() => {
              setOpenForm(EMPTY_OPEN)
              setOpenError(null)
              setOpenModal(true)
            }}
          >
            Nova OS
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <button
          onClick={() => {
            setStatus('')
            setPage(0)
          }}
          className={`rounded-full border px-3 py-1 text-xs ${status === '' ? 'border-primary bg-primary/10' : 'border-border'}`}
        >
          Todas
        </button>
        {OS_STATUSES.map((s) => (
          <button
            key={s.id}
            onClick={() => {
              setStatus(s.id)
              setPage(0)
            }}
            className={`rounded-full border px-3 py-1 text-xs ${status === s.id ? 'border-primary bg-primary/10' : 'border-border'}`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar as ordens.</p>
      ) : isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma ordem de serviço encontrada.</p>
      ) : (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((os: ServiceOrder) => (
            <button
              key={os.id}
              onClick={() => setDetailId(os.id)}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/40"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{os.vehiclePlate}</span>
                  {os.vehicleModel && (
                    <span className="text-xs text-muted-foreground">{os.vehicleModel}</span>
                  )}
                  <StatusBadge status={os.status} />
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {os.customerName} · {os.complaint}
                  {os.mechanicName ? ` · ${os.mechanicName}` : ''}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <div className="text-sm font-medium">{formatPrice(os.totalCents)}</div>
                <div className="text-xs text-muted-foreground">{formatDate(os.openedAt)}</div>
              </div>
            </button>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} OS
          </span>
          <div className="flex gap-1">
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ←
            </Button>
            <Button
              variant="outline"
              className="h-7 px-2 text-xs"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              →
            </Button>
          </div>
        </div>
      )}

      {/* Modal: nova OS */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title="Nova ordem de serviço"
        size="md"
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            openMutation.mutate()
          }}
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Veículo
              </label>
              <select
                value={openForm.vehicleId}
                onChange={(e) => setOpenForm((f) => ({ ...f, vehicleId: e.target.value }))}
                required
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Selecione…</option>
                {(vehicles.data?.items ?? []).map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.plate}
                    {v.model ? ` · ${v.model}` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Mecânico (opcional)
              </label>
              <select
                value={openForm.mechanicId}
                onChange={(e) => setOpenForm((f) => ({ ...f, mechanicId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Sem atribuição</option>
                {(mechanics.data?.items ?? []).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Queixa do cliente
            </label>
            <textarea
              value={openForm.complaint}
              onChange={(e) => setOpenForm((f) => ({ ...f, complaint: e.target.value }))}
              required
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Observações
            </label>
            <textarea
              value={openForm.notes}
              onChange={(e) => setOpenForm((f) => ({ ...f, notes: e.target.value }))}
              rows={2}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {openError && <p className="text-sm text-destructive">{openError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setOpenModal(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={openMutation.isPending}>
              {openMutation.isPending ? 'Abrindo…' : 'Abrir OS'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: detalhe + itens + status */}
      <Modal
        open={detailId !== null}
        onClose={() => {
          setDetailId(null)
          setStatusError(null)
          setItemError(null)
        }}
        title="Ordem de serviço"
        size="lg"
      >
        {detail.isPending || !o ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">{o.vehiclePlate}</span>
              {o.vehicleModel && (
                <span className="text-xs text-muted-foreground">{o.vehicleModel}</span>
              )}
              <StatusBadge status={o.status} />
            </div>
            <Card>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">Cliente</dt>
                  <dd>{o.customerName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Telefone</dt>
                  <dd>{o.customerPhone ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Mecânico</dt>
                  <dd>{o.mechanicName ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">Origem</dt>
                  <dd>{o.conversationId ? 'WhatsApp' : 'Manual'}</dd>
                </div>
                <div className="col-span-2">
                  <dt className="text-xs text-muted-foreground">Queixa</dt>
                  <dd>{o.complaint}</dd>
                </div>
                {o.notes && (
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">Observações</dt>
                    <dd>{o.notes}</dd>
                  </div>
                )}
              </dl>
            </Card>

            {/* Itens */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">Itens</h3>
                <span className="text-sm font-medium">Total: {formatPrice(o.totalCents)}</span>
              </div>
              {o.items.length === 0 ? (
                <p className="text-xs text-muted-foreground">Nenhum item ainda.</p>
              ) : (
                <div className="divide-y divide-border rounded-lg border border-border">
                  {o.items.map((it) => (
                    <div
                      key={it.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <div className="min-w-0">
                        <span className="font-medium">{it.description}</span>
                        <span className="ml-2 text-xs text-muted-foreground">
                          {kindLabel(it.kind)} · {it.quantity} × {formatPrice(it.unitPriceCents)}
                        </span>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <span>{formatPrice(it.lineTotalCents)}</span>
                        {!itemsLocked && (
                          <Button
                            variant="outline"
                            className="h-6 px-2 text-xs"
                            disabled={deleteItemMutation.isPending}
                            onClick={() => deleteItemMutation.mutate(it.id)}
                          >
                            Remover
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {!itemsLocked && (
                <form
                  className="flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-border p-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    addItemMutation.mutate()
                  }}
                >
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Tipo
                    </label>
                    <select
                      value={itemForm.kind}
                      onChange={(e) =>
                        setItemForm((f) => ({ ...f, kind: e.target.value as OsItemKind }))
                      }
                      className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    >
                      <option value="peca">Peça</option>
                      <option value="mao_de_obra">Mão de obra</option>
                    </select>
                  </div>
                  <div className="min-w-[8rem] flex-1">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Descrição
                    </label>
                    <input
                      value={itemForm.description}
                      onChange={(e) => setItemForm((f) => ({ ...f, description: e.target.value }))}
                      required
                      maxLength={200}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-16">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Qtd
                    </label>
                    <input
                      type="number"
                      min="1"
                      value={itemForm.quantity}
                      onChange={(e) => setItemForm((f) => ({ ...f, quantity: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <div className="w-24">
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">
                      Unit. (R$)
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={itemForm.price}
                      required
                      onChange={(e) => setItemForm((f) => ({ ...f, price: e.target.value }))}
                      className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    className="h-8 px-3 text-xs"
                    disabled={addItemMutation.isPending}
                  >
                    Adicionar
                  </Button>
                </form>
              )}
              {itemError && <p className="text-sm text-destructive">{itemError}</p>}
            </div>

            {/* Status */}
            {ALLOWED_NEXT[o.status].length > 0 ? (
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">
                  Mudar status para…
                </label>
                <div className="flex flex-wrap gap-2">
                  {ALLOWED_NEXT[o.status].map((next) => (
                    <Button
                      key={next}
                      variant="outline"
                      className="h-8 px-3 text-xs"
                      onClick={() => setStatusTarget(next)}
                    >
                      {statusLabel(next)}
                    </Button>
                  ))}
                </div>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">Esta OS está num status final.</p>
            )}
            {statusError && <p className="text-sm text-destructive">{statusError}</p>}
          </div>
        )}
      </Modal>

      <AlertDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={`Mudar status para "${statusTarget ? statusLabel(statusTarget) : ''}"?`}
        description="O cliente é notificado automaticamente em orçamento, aprovação, conclusão e entrega (se houver vínculo com o WhatsApp)."
        confirmLabel="Mudar status"
        destructive={false}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusTarget) statusMutation.mutate(statusTarget)
        }}
      />
    </div>
  )
}
