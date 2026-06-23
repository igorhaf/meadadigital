'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { listOrders, updateOrderStatus } from '@/lib/api/sushi/orders'
import {
  KANBAN_COLUMNS,
  NEXT_STATUS,
  STATUS_LABEL,
  formatBrl,
  type Order,
  type OrderStatus,
} from '@/profiles/sushi/sushi-types'

/** Resumo curto dos itens de um pedido. */
function itemsSummary(order: Order): string {
  return order.items.map((i) => `${i.qtd}× ${i.itemName}`).join(', ')
}

/** Card de um pedido no Kanban. */
function OrderCard({
  order,
  onAdvance,
  onCancel,
  busy,
}: {
  order: Order
  onAdvance: (o: Order) => void
  onCancel: (o: Order) => void
  busy: boolean
}) {
  const next = NEXT_STATUS[order.status]
  return (
    <Card className="space-y-2 p-3">
      <div className="flex items-center justify-between">
        <span className="font-mono text-xs text-muted-foreground">#{order.id.slice(0, 8)}</span>
        <span className="text-xs text-muted-foreground">
          {new Date(order.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
      <p className="text-sm font-medium">{order.contactName ?? 'Cliente'}</p>
      <p className="line-clamp-2 text-xs text-muted-foreground">{itemsSummary(order)}</p>
      <p className="text-xs text-muted-foreground">{order.deliveryAddress}</p>
      <p className="text-sm font-semibold tabular-nums">{formatBrl(order.totalCents)}</p>
      <div className="flex gap-1 pt-1">
        {next && (
          <Button className="h-7 flex-1 px-2 text-xs" disabled={busy} onClick={() => onAdvance(order)}>
            Avançar → {STATUS_LABEL[next]}
          </Button>
        )}
        <Button variant="outline" className="h-7 px-2 text-xs" disabled={busy} onClick={() => onCancel(order)}>
          Cancelar
        </Button>
      </div>
    </Card>
  )
}

/**
 * Kanban de pedidos do SushiBot (camada 7.1). Colunas: Recebido → Em preparo → Saiu pra entrega.
 * Avançar move pro próximo status; cancelar confirma via AlertDialog. Entregues/cancelados ficam
 * na aba Histórico. Atualiza a cada 30s.
 */
export default function OrdersPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'andamento' | 'historico'>('andamento')
  const [cancelTarget, setCancelTarget] = useState<Order | null>(null)

  // Em andamento: busca tudo e separa client-side pelas colunas (poucos pedidos ativos).
  const active = useQuery({
    queryKey: ['sushi-orders', 'active'],
    queryFn: () => listOrders({ pageSize: 100 }),
    refetchInterval: 30_000,
  })

  const history = useQuery({
    queryKey: ['sushi-orders', 'history'],
    queryFn: () => listOrders({ pageSize: 100 }),
    enabled: tab === 'historico',
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: OrderStatus }) => updateOrderStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sushi-orders'] }),
  })

  function advance(o: Order) {
    const next = NEXT_STATUS[o.status]
    if (next) statusMutation.mutate({ id: o.id, status: next })
  }

  const allActive = active.data?.items ?? []
  const historyItems = (history.data?.items ?? []).filter(
    (o) => o.status === 'entregue' || o.status === 'cancelado',
  )

  return (
    <div className="space-y-6">
      <PageHeader title="Pedidos" description="Acompanhe e mova os pedidos pelo fluxo de preparo e entrega." />

      <div className="flex gap-2">
        <Button variant={tab === 'andamento' ? 'default' : 'outline'} onClick={() => setTab('andamento')}>
          Em andamento
        </Button>
        <Button variant={tab === 'historico' ? 'default' : 'outline'} onClick={() => setTab('historico')}>
          Histórico
        </Button>
      </div>

      {tab === 'andamento' ? (
        active.isError ? (
          <p className="text-sm text-destructive">Erro ao carregar os pedidos.</p>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {KANBAN_COLUMNS.map((col) => {
              const colOrders = allActive.filter((o) => o.status === col.status)
              return (
                <div key={col.status} className="space-y-3">
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
                <div key={o.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                  <span className="font-mono text-xs text-muted-foreground">#{o.id.slice(0, 8)}</span>
                  <span className="min-w-0 flex-1 truncate">{o.contactName ?? 'Cliente'} · {itemsSummary(o)}</span>
                  <span className="tabular-nums">{formatBrl(o.totalCents)}</span>
                  <Badge variant={o.status === 'entregue' ? 'success' : 'danger'}>
                    {STATUS_LABEL[o.status]}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

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
    </div>
  )
}
