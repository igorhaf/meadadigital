'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { ApiError } from '@/lib/api/client'
import { listOrders, updateDeposit, updateOrderStatus } from '@/lib/api/padaria/orders'
import { useKanbanDnd } from '@/lib/kanban/use-kanban-dnd'
import { fulfillmentLabel } from '@/profiles/padaria/padaria-fulfillment'
import { periodLabel } from '@/profiles/padaria/padaria-period'
import {
  formatBrl,
  KANBAN_COLUMNS,
  nextStatus,
  STATUS_LABEL,
  type Order,
  type OrderItem,
  type OrderStatus,
} from '@/profiles/padaria/padaria-types'

/** Resumo de um item com seus modifiers: "1× Bolo (Chocolate, Ninho)". */
function itemLine(it: OrderItem): string {
  const opts = it.options.map((o) => o.optionLabel).join(', ')
  return `${it.qtd}× ${it.itemName}${opts ? ` (${opts})` : ''}`
}

/** Resumo curto (uma linha) dos itens de um pedido. */
function itemsSummary(order: Order): string {
  return order.items.map(itemLine).join(', ')
}

/** Data agendada (retirada/entrega) + período, em pt-BR. Vazio se o pedido não tem agenda. */
function scheduleLine(order: Order): string | null {
  if (!order.pickupOrDeliveryDate) return null
  // pickupOrDeliveryDate vem como YYYY-MM-DD (sem fuso); formata sem deslocar o dia.
  const [y, m, d] = order.pickupOrDeliveryDate.split('-')
  const date = d && m && y ? `${d}/${m}/${y}` : order.pickupOrDeliveryDate
  const period = order.deliveryPeriod ? ` · ${periodLabel(order.deliveryPeriod)}` : ''
  return `${date}${period}`
}

/** Card de um pedido no Kanban. Em "aguardando" exibe Aceitar/Recusar; senão Avançar/Cancelar. */
function OrderCard({
  order,
  onAccept,
  onDeposit,
  onReject,
  onAdvance,
  onCancel,
  busy,
  dragProps,
}: {
  order: Order
  onAccept: (o: Order) => void
  onDeposit: (o: Order) => void
  onReject: (o: Order) => void
  onAdvance: (o: Order) => void
  onCancel: (o: Order) => void
  busy: boolean
  dragProps?: React.HTMLAttributes<HTMLDivElement> & { draggable?: boolean }
}) {
  const next = nextStatus(order.status, order.fulfillment)
  const awaiting = order.status === 'aguardando'
  const schedule = scheduleLine(order)
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
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm font-medium">{order.contactName ?? 'Cliente'}</p>
          <Badge variant={order.fulfillment === 'entrega' ? 'info' : 'muted'}>
            {fulfillmentLabel(order.fulfillment)}
          </Badge>
        </div>
        <ul className="space-y-0.5 text-xs text-muted-foreground">
          {order.items.map((it) => (
            <li key={it.id} className="line-clamp-2">
              {itemLine(it)}
              {it.madeToOrder && (
                <span className="ml-1 text-[10px] text-amber-600 uppercase">· encomenda</span>
              )}
              {it.cakeMessage && <span className="block italic">Placa: “{it.cakeMessage}”</span>}
            </li>
          ))}
        </ul>
        {schedule && <p className="text-xs font-medium text-muted-foreground">📅 {schedule}</p>}
        {order.fulfillment === 'entrega' && order.deliveryAddress && (
          <p className="text-xs text-muted-foreground">{order.deliveryAddress}</p>
        )}
        {order.depositCents != null && order.depositCents > 0 && (
          <p className="text-xs">
            <Badge variant={order.depositPaid ? 'success' : 'warning'}>
              {order.depositPaid ? 'Sinal recebido' : 'Sinal pendente'}
            </Badge>
            <span className="ml-1 text-muted-foreground">{formatBrl(order.depositCents)}</span>
          </p>
        )}
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
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={busy}
                onClick={() => onDeposit(order)}
              >
                Sinal
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
 * Kanban de pedidos do PadariaBot. Colunas: Aguardando aceite → Em preparo → Pronto → Saiu pra
 * entrega. Gate de aceite: em "aguardando" o lojista Aceita (→ em_preparo) ou Recusa (Modal com
 * motivo OPCIONAL → status recusado). FUNIL DIVERGE pela forma de entrega: em "pronto", retirada
 * avança pra "retirado" (terminal); entrega avança pra "saiu_entrega" → "entregue". Nas demais
 * colunas: Avançar / Cancelar (confirmado via AlertDialog). Retirados/entregues/recusados/
 * cancelados ficam na aba Histórico (recusado mostra o motivo). Atualiza a cada 30s.
 */
export default function PadariaOrdersPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'andamento' | 'historico'>('andamento')
  const [cancelTarget, setCancelTarget] = useState<Order | null>(null)
  const [rejectTarget, setRejectTarget] = useState<Order | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  // Em andamento: busca tudo e separa client-side pelas colunas (poucos pedidos ativos).
  const active = useQuery({
    queryKey: ['padaria-orders', 'active'],
    queryFn: () => listOrders({ pageSize: 100 }),
    refetchInterval: 30_000,
  })

  const history = useQuery({
    queryKey: ['padaria-orders', 'history'],
    queryFn: () => listOrders({ pageSize: 100 }),
    enabled: tab === 'historico',
  })

  const [depositTarget, setDepositTarget] = useState<Order | null>(null)
  const [depositValue, setDepositValue] = useState('')
  const [depositPaid, setDepositPaid] = useState(false)
  const [depositError, setDepositError] = useState<string | null>(null)

  const depositMutation = useMutation({
    mutationFn: async (order: Order) => {
      const cents = depositValue === '' ? null : Math.round(Number(depositValue) * 100)
      return updateDeposit(order.id, { depositCents: cents, depositPaid })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['padaria-orders'] })
      setDepositTarget(null)
      setDepositError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'invalid_deposit')
        setDepositError('Para marcar como recebido, informe um valor de sinal maior que zero.')
      else setDepositError('Erro ao salvar o sinal.')
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status, reason }: { id: string; status: OrderStatus; reason?: string }) =>
      updateOrderStatus(id, status, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['padaria-orders'] }),
  })

  function accept(o: Order) {
    statusMutation.mutate({ id: o.id, status: 'em_preparo' })
  }

  function advance(o: Order) {
    const next = nextStatus(o.status, o.fulfillment)
    if (next) statusMutation.mutate({ id: o.id, status: next })
  }

  const busy = statusMutation.isPending || depositMutation.isPending

  const allActiveForDnd = active.data?.items ?? []

  // Drag-and-drop: soltar um card numa coluna avança o pedido — SÓ nas transições válidas do funil
  // (nextStatus, que diverge por fulfillment em "pronto"). 'aguardando' é gate de aceite (botão);
  // recusar/cancelar continuam por botão (exigem motivo/confirmação).
  const dnd = useKanbanDnd({
    canDrop: (id, target) => {
      const o = allActiveForDnd.find((x) => x.id === id)
      if (!o || busy) return false
      if (o.status === 'aguardando') return false // aceitar/recusar = botão
      return nextStatus(o.status, o.fulfillment) === target
    },
    onDrop: (id, target) => {
      statusMutation.mutate({ id, status: target as OrderStatus })
    },
  })

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
  const historyItems = (history.data?.items ?? []).filter(
    (o) =>
      o.status === 'retirado' ||
      o.status === 'entregue' ||
      o.status === 'recusado' ||
      o.status === 'cancelado',
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pedidos"
        description="Aceite ou recuse novos pedidos e acompanhe o preparo, a retirada e a entrega."
      />

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
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
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
                          busy={busy}
                          dragProps={dnd.cardProps(o.id)}
                          onDeposit={(ord) => {
                            setDepositTarget(ord)
                            setDepositValue(
                              ord.depositCents != null ? String(ord.depositCents / 100) : '',
                            )
                            setDepositPaid(ord.depositPaid)
                            setDepositError(null)
                          }}
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
                    <Badge variant={o.fulfillment === 'entrega' ? 'info' : 'muted'}>
                      {fulfillmentLabel(o.fulfillment)}
                    </Badge>
                    <span className="tabular-nums">{formatBrl(o.totalCents)}</span>
                    <Badge
                      variant={
                        o.status === 'retirado' || o.status === 'entregue' ? 'success' : 'danger'
                      }
                    >
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

      {/* Sinal/entrada (onda #1 — registro manual até o gateway #50). */}
      <Modal
        open={depositTarget !== null}
        onClose={() => setDepositTarget(null)}
        title="Sinal / entrada da encomenda"
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Com sinal registrado e não recebido, o ACEITE da encomenda fica bloqueado até a
            confirmação (Pix/dinheiro conferido fora do app, por enquanto).
          </p>
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-32">
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Valor (R$)
              </label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={depositValue}
                onChange={(e) => setDepositValue(e.target.value)}
                placeholder="0,00"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
            </div>
            <label className="flex h-9 items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={depositPaid}
                onChange={(e) => setDepositPaid(e.target.checked)}
              />
              Recebido
            </label>
          </div>
          {depositError && <p className="text-sm text-destructive">{depositError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setDepositTarget(null)}>
              Voltar
            </Button>
            <Button
              disabled={depositMutation.isPending}
              onClick={() => depositTarget && depositMutation.mutate(depositTarget)}
            >
              {depositMutation.isPending ? 'Salvando…' : 'Salvar sinal'}
            </Button>
          </div>
        </div>
      </Modal>

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
              placeholder="Ex.: item em falta, sem prazo para a encomenda, fora da área de entrega…"
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
