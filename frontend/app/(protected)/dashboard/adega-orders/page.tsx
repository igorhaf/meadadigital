'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { listOrders, updateOrderStatus } from '@/lib/api/adega/orders'
import { ApiError } from '@/lib/api/client'
import { useKanbanDnd } from '@/lib/kanban/use-kanban-dnd'
import {
  formatBrl,
  KANBAN_COLUMNS,
  NEXT_STATUS,
  STATUS_LABEL,
  type Order,
  type OrderItem,
  type OrderStatus,
} from '@/profiles/adega/adega-types'

/** Resumo de um item com seus modifiers: "1× Tinto Reserva (1L), Gelado". */
function itemLine(it: OrderItem): string {
  const opts = it.options.map((o) => o.optionLabel).join(', ')
  return `${it.qtd}× ${it.itemName}${opts ? ` (${opts})` : ''}`
}

/** Resumo curto (multilinha) dos itens de um pedido. */
function itemsSummary(order: Order): string {
  return order.items.map(itemLine).join(', ')
}

/** Card de um pedido no Kanban. Em "aguardando" exibe Aceitar/Recusar; senão Avançar/Cancelar. */
function OrderCard({
  order,
  onAccept,
  onReject,
  onAdvance,
  onCancel,
  busy,
  dragProps,
}: {
  order: Order
  onAccept: (o: Order) => void
  onReject: (o: Order) => void
  onAdvance: (o: Order) => void
  onCancel: (o: Order) => void
  busy: boolean
  dragProps?: React.HTMLAttributes<HTMLDivElement> & { draggable?: boolean }
}) {
  const next = NEXT_STATUS[order.status]
  const awaiting = order.status === 'aguardando'
  return (
    <div
      {...dragProps}
      className="data-[dragging=true]:opacity-50 [&[draggable=true]]:cursor-grab active:[&[draggable=true]]:cursor-grabbing"
    >
      <Card className="space-y-2 p-3">
        <div className="flex items-center justify-between">
          <span className="font-mono text-xs text-muted-foreground">#{order.id.slice(0, 8)}</span>
          <span className="text-xs text-muted-foreground">
            {new Date(order.createdAt).toLocaleTimeString('pt-BR', {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium">{order.contactName ?? 'Cliente'}</p>
          {/* ESCAPADA adega (+18): selo de conformidade de maioridade. */}
          {order.ageConfirmed && <Badge variant="success">+18 confirmado</Badge>}
        </div>
        <ul className="space-y-0.5 text-xs text-muted-foreground">
          {order.items.map((it) => (
            <li key={it.id} className="line-clamp-1">
              {itemLine(it)}
            </li>
          ))}
        </ul>
        <p className="text-xs text-muted-foreground">{order.deliveryAddress}</p>
        <p className="text-sm font-semibold tabular-nums">{formatBrl(order.totalCents)}</p>
        <div className="flex gap-1 pt-1">
          {awaiting ? (
            <>
              <Button
                className="h-7 flex-1 px-2 text-xs"
                disabled={busy}
                onClick={() => onAccept(order)}
              >
                Aceitar
              </Button>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={busy}
                onClick={() => onReject(order)}
              >
                Recusar
              </Button>
            </>
          ) : (
            <>
              {next && (
                <Button
                  className="h-7 flex-1 px-2 text-xs"
                  disabled={busy}
                  onClick={() => onAdvance(order)}
                >
                  Avançar → {STATUS_LABEL[next]}
                </Button>
              )}
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={busy}
                onClick={() => onCancel(order)}
              >
                Cancelar
              </Button>
            </>
          )}
        </div>
      </Card>
    </div>
  )
}

/**
 * Kanban de pedidos do AdegaBot (delivery de bebidas). Colunas: Aguardando aceite → Em preparo →
 * Saiu pra entrega. Gate de aceite: em "aguardando" o lojista Aceita (→ em_preparo) ou Recusa
 * (Modal com motivo OPCIONAL → status recusado). Nas demais colunas: Avançar / Cancelar (confirmado
 * via AlertDialog). Entregues/recusados/cancelados ficam na aba Histórico (recusado mostra o motivo).
 * ESCAPADA adega (+18): cada card/linha exibe o selo "+18 confirmado" lendo order.ageConfirmed
 * (sempre true — o backend recusa criar pedido sem confirmação; é uma afordância de conformidade).
 * Atualiza a cada 30s.
 */
export default function AdegaOrdersPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'andamento' | 'historico'>('andamento')
  const [cancelTarget, setCancelTarget] = useState<Order | null>(null)
  const [rejectTarget, setRejectTarget] = useState<Order | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  // Em andamento: busca tudo e separa client-side pelas colunas (poucos pedidos ativos).
  const active = useQuery({
    queryKey: ['adega-orders', 'active'],
    queryFn: () => listOrders({ pageSize: 100 }),
    refetchInterval: 30_000,
  })

  const history = useQuery({
    queryKey: ['adega-orders', 'history'],
    queryFn: () => listOrders({ pageSize: 100 }),
    enabled: tab === 'historico',
  })

  const [statusError, setStatusError] = useState<string | null>(null)
  const statusMutation = useMutation({
    mutationFn: ({ id, status, reason }: { id: string; status: OrderStatus; reason?: string }) =>
      updateOrderStatus(id, status, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['adega-orders'] })
      setStatusError(null)
    },
    onError: (e) => {
      // Falha silenciosa deixava o tenant sem saber que a ação não valeu; re-busca o estado real.
      qc.invalidateQueries({ queryKey: ['adega-orders'] })
      setStatusError(
        e instanceof ApiError && e.reason === 'invalid_status_transition'
          ? 'O status já mudou em outra tela — a lista foi atualizada.'
          : 'Erro ao atualizar o status. Tente novamente.',
      )
    },
  })

  function accept(o: Order) {
    statusMutation.mutate({ id: o.id, status: 'em_preparo' })
  }

  function advance(o: Order) {
    const next = NEXT_STATUS[o.status]
    if (next) statusMutation.mutate({ id: o.id, status: next })
  }

  function confirmReject() {
    if (!rejectTarget) return
    statusMutation.mutate({
      id: rejectTarget.id,
      status: 'recusado',
      reason: rejectReason.trim() || undefined,
    })
    setRejectTarget(null)
    setRejectReason('')
  }

  const allActive = active.data?.items ?? []

  // Drag-and-drop: soltar um card numa coluna avança o pedido — SÓ se for a transição válida
  // (NEXT_STATUS do status atual). Recusar/cancelar continuam por botão.
  const dnd = useKanbanDnd({
    canDrop: (id, target) => {
      const o = allActive.find((x) => x.id === id)
      if (!o || statusMutation.isPending) return false
      return NEXT_STATUS[o.status] === target
    },
    onDrop: (id, target) => {
      statusMutation.mutate({ id, status: target as OrderStatus })
    },
  })

  const historyItems = (history.data?.items ?? []).filter(
    (o) => o.status === 'entregue' || o.status === 'recusado' || o.status === 'cancelado',
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pedidos"
        description="Aceite ou recuse novos pedidos e acompanhe o preparo e a entrega."
      />

      {statusError && <p className="text-sm text-destructive">{statusError}</p>}

      <div className="flex gap-2">
        <Button
          variant={tab === 'andamento' ? 'default' : 'outline'}
          onClick={() => setTab('andamento')}
        >
          Em andamento
        </Button>
        <Button
          variant={tab === 'historico' ? 'default' : 'outline'}
          onClick={() => setTab('historico')}
        >
          Histórico
        </Button>
      </div>

      {tab === 'andamento' ? (
        active.isError ? (
          <p className="text-sm text-destructive">Erro ao carregar os pedidos.</p>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {KANBAN_COLUMNS.map((col) => {
              const colOrders = allActive.filter((o) => o.status === col.id)
              return (
                <div
                  key={col.id}
                  {...dnd.columnProps(col.id)}
                  className="space-y-3 rounded-lg p-1 transition-colors data-[over=true]:bg-[var(--palette-surface)] data-[over=true]:ring-2 data-[over=true]:ring-primary/40"
                >
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm font-semibold">{col.label}</h2>
                    <Badge variant="muted">{colOrders.length}</Badge>
                  </div>
                  <div className="space-y-3">
                    {colOrders.length === 0 ? (
                      <p className="text-xs text-muted-foreground">Nenhum pedido.</p>
                    ) : (
                      colOrders.map((o) => (
                        <OrderCard
                          key={o.id}
                          order={o}
                          busy={statusMutation.isPending}
                          dragProps={dnd.cardProps(o.id)}
                          onAccept={accept}
                          onReject={(ord) => {
                            setRejectReason('')
                            setRejectTarget(ord)
                          }}
                          onAdvance={advance}
                          onCancel={setCancelTarget}
                        />
                      ))
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )
      ) : (
        <div className="space-y-2">
          {historyItems.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum pedido finalizado ainda.</p>
          ) : (
            <div className="divide-y divide-border rounded-lg border border-border">
              {historyItems.map((o) => (
                <div key={o.id} className="flex flex-col gap-1 px-4 py-3 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-mono text-xs text-muted-foreground">
                      #{o.id.slice(0, 8)}
                    </span>
                    <span className="min-w-0 flex-1 truncate">
                      {o.contactName ?? 'Cliente'} · {itemsSummary(o)}
                    </span>
                    {o.ageConfirmed && <Badge variant="success">+18 confirmado</Badge>}
                    <span className="tabular-nums">{formatBrl(o.totalCents)}</span>
                    <Badge variant={o.status === 'entregue' ? 'success' : 'danger'}>
                      {STATUS_LABEL[o.status]}
                    </Badge>
                  </div>
                  {o.status === 'recusado' && o.rejectionReason && (
                    <p className="text-xs text-muted-foreground">Motivo: {o.rejectionReason}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Cancelar (demais colunas): confirmação simples via AlertDialog. */}
      <AlertDialog
        open={cancelTarget !== null}
        onOpenChange={(open) => !open && setCancelTarget(null)}
        title="Cancelar pedido?"
        description="O cliente será notificado do cancelamento. Esta ação não pode ser desfeita."
        confirmLabel="Cancelar pedido"
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (cancelTarget) {
            statusMutation.mutate({ id: cancelTarget.id, status: 'cancelado' })
            setCancelTarget(null)
          }
        }}
      />

      {/* Recusar (gate de aceite): Modal com motivo OPCIONAL (AlertDialog não tem campo de texto livre). */}
      <Modal
        open={rejectTarget !== null}
        onClose={() => {
          setRejectTarget(null)
          setRejectReason('')
        }}
        title="Recusar pedido?"
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            O cliente será notificado da recusa. O motivo é opcional e, se informado, é enviado ao
            cliente.
          </p>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">
              Motivo (opcional)
            </label>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              rows={3}
              maxLength={300}
              placeholder="Ex.: item em falta, fora da área de entrega…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setRejectTarget(null)
                setRejectReason('')
              }}
            >
              Voltar
            </Button>
            <Button
              variant="destructive"
              disabled={statusMutation.isPending}
              onClick={confirmReject}
            >
              {statusMutation.isPending ? 'Recusando…' : 'Recusar pedido'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
