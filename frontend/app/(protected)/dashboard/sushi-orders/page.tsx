'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { listOrderStatuses } from '@/lib/api/sushi/order-statuses'
import { listOrders, updateOrderStatus } from '@/lib/api/sushi/orders'
import { formatBrl, type Order, type OrderStatusDef } from '@/profiles/sushi/sushi-types'

/** Resumo curto dos itens de um pedido. */
function itemsSummary(order: Order): string {
  return order.items.map((i) => `${i.qtd}× ${i.itemName}`).join(', ')
}

/** Badge de agendamento: "Agendado: DD/MM tarde" quando há data; senão "Para agora". */
function ScheduleBadge({ order }: { order: Order }) {
  if (order.scheduledDate) {
    const d = new Date(order.scheduledDate).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
    })
    return (
      <Badge variant="warning">
        Agendado: {d}
        {order.scheduledPeriod ? ` ${order.scheduledPeriod}` : ''}
      </Badge>
    )
  }
  return <Badge variant="muted">Para agora</Badge>
}

/** Card de um pedido no Kanban, com o seletor de status (não-linear). */
function OrderCard({
  order,
  statuses,
  onChangeStatus,
  busy,
}: {
  order: Order
  statuses: OrderStatusDef[]
  onChangeStatus: (orderId: string, statusId: string) => void
  busy: boolean
}) {
  return (
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
      <p className="text-sm font-medium">{order.contactName ?? 'Cliente'}</p>
      <p className="line-clamp-2 text-xs text-muted-foreground">{itemsSummary(order)}</p>

      <div className="flex flex-wrap gap-1">
        <Badge variant={order.fulfillment === 'entrega' ? 'info' : 'default'}>
          {order.fulfillment === 'entrega' ? 'Entrega' : 'Retirada'}
        </Badge>
        <ScheduleBadge order={order} />
        {order.couponCode && <Badge variant="success">{order.couponCode}</Badge>}
        {order.loyaltyApplied && <Badge variant="success">Fidelidade</Badge>}
      </div>

      {order.fulfillment === 'entrega' && order.deliveryAddress && (
        <p className="text-xs text-muted-foreground">{order.deliveryAddress}</p>
      )}

      {order.discountCents > 0 && (
        <p className="text-xs text-emerald-600">Desconto: −{formatBrl(order.discountCents)}</p>
      )}
      <p className="text-sm font-semibold tabular-nums">{formatBrl(order.totalCents)}</p>

      <div className="pt-1">
        <label className="mb-1 block text-[10px] tracking-wide text-muted-foreground uppercase">
          Mover para
        </label>
        <select
          value={order.status}
          disabled={busy}
          onChange={(e) => {
            if (e.target.value !== order.status) onChangeStatus(order.id, e.target.value)
          }}
          className="w-full rounded-md border border-border bg-background px-2 py-1 text-xs"
        >
          {statuses.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
    </Card>
  )
}

/**
 * Kanban de pedidos do SushiBot (reworkado, dinâmico). As colunas vêm da API de status do tenant:
 * status NÃO-terminais (ordenados por sortOrder) viram colunas; status terminais agrupam o
 * "Histórico". O avanço linear foi substituído por um SELETOR de status por card — o tenant move o
 * pedido para qualquer status (o backend bloqueia transições inválidas, ex. sair de um terminal).
 * Atualiza a cada 30s.
 */
export default function OrdersPage() {
  const qc = useQueryClient()

  const statusesQuery = useQuery({
    queryKey: ['sushi-order-statuses'],
    queryFn: () => listOrderStatuses(),
  })

  const ordersQuery = useQuery({
    queryKey: ['sushi-orders'],
    queryFn: () => listOrders({ pageSize: 200 }),
    refetchInterval: 30_000,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, statusId }: { id: string; statusId: string }) =>
      updateOrderStatus(id, statusId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sushi-orders'] }),
  })

  const statuses = useMemo(
    () => [...(statusesQuery.data?.items ?? [])].sort((a, b) => a.sortOrder - b.sortOrder),
    [statusesQuery.data],
  )
  const columns = statuses.filter((s) => !s.isTerminal)
  const terminalIds = useMemo(
    () => new Set(statuses.filter((s) => s.isTerminal).map((s) => s.id)),
    [statuses],
  )

  const orders = ordersQuery.data?.items ?? []
  const terminalOrders = orders.filter((o) => terminalIds.has(o.status))

  function changeStatus(id: string, statusId: string) {
    statusMutation.mutate({ id, statusId })
  }

  const loading = statusesQuery.isPending || ordersQuery.isPending
  const error = statusesQuery.isError || ordersQuery.isError

  return (
    <div className="space-y-8">
      <PageHeader
        title="Pedidos"
        description="Acompanhe e mova os pedidos pelos status definidos no seu fluxo."
      />

      {error ? (
        <p className="text-sm text-destructive">Erro ao carregar os pedidos.</p>
      ) : loading ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : columns.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Nenhum status configurado. Cadastre status em &ldquo;Status &amp; Notificações&rdquo;.
        </p>
      ) : (
        <>
          <div
            className="grid grid-cols-1 gap-4"
            style={{
              gridTemplateColumns: `repeat(${Math.min(columns.length, 4)}, minmax(0, 1fr))`,
            }}
          >
            {columns.map((col) => {
              const colOrders = orders.filter((o) => o.status === col.id)
              return (
                <div key={col.id} className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      {col.color && (
                        <span
                          className="h-2.5 w-2.5 rounded-full border border-border"
                          style={{ backgroundColor: col.color }}
                        />
                      )}
                      <h2 className="text-sm font-semibold">{col.name}</h2>
                    </div>
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
                          statuses={statuses}
                          busy={statusMutation.isPending}
                          onChangeStatus={changeStatus}
                        />
                      ))
                    )}
                  </div>
                </div>
              )
            })}
          </div>

          <section className="space-y-2">
            <h2 className="text-sm font-semibold text-muted-foreground">Histórico</h2>
            {terminalOrders.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nenhum pedido finalizado ainda.</p>
            ) : (
              <div className="divide-y divide-border rounded-lg border border-border">
                {terminalOrders.map((o) => (
                  <div
                    key={o.id}
                    className="flex items-center justify-between gap-3 px-4 py-3 text-sm"
                  >
                    <span className="font-mono text-xs text-muted-foreground">
                      #{o.id.slice(0, 8)}
                    </span>
                    <span className="min-w-0 flex-1 truncate">
                      {o.contactName ?? 'Cliente'} · {itemsSummary(o)}
                    </span>
                    <span className="tabular-nums">{formatBrl(o.totalCents)}</span>
                    <Badge variant="muted">{o.statusName}</Badge>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
