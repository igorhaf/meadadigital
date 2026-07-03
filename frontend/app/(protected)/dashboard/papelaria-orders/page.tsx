'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { useKanbanDnd } from '@/lib/kanban/use-kanban-dnd'
import { approveArt, listOrders, setArtUrl, updateOrderStatus } from '@/lib/api/papelaria/orders'
import { fulfillmentLabel } from '@/profiles/papelaria/papelaria-fulfillment'
import { periodLabel } from '@/profiles/papelaria/papelaria-period'
import {
  KANBAN_COLUMNS,
  STATUS_LABEL,
  formatBrl,
  nextStatus,
  type Order,
  type OrderItem,
  type OrderStatus,
} from '@/profiles/papelaria/papelaria-types'

/** Resumo de um item com tiragem + modifiers: "100× Convite (Couché 250g, Dourado)". */
function itemLine(it: OrderItem): string {
  const opts = it.options.map((o) => o.optionLabel).join(', ')
  return `${it.quantity}× ${it.itemName}${opts ? ` (${opts})` : ''}`
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

/**
 * Card de um pedido no Kanban. As ações variam por coluna:
 *  - aguardando     → Aceitar / Recusar (gate de aceite)
 *  - aceito         → Subir arte (→ arte_aprovacao) / Cancelar (PROVA DE ARTE — ESCAPADA 8.15)
 *  - arte_aprovacao → Marcar arte aprovada + Enviar pra produção (desabilitado se !artApproved) / Cancelar
 *  - demais         → Avançar → <próximo> / Cancelar
 */
function OrderCard({
  order,
  onAccept,
  onReject,
  onUploadArt,
  onApproveArt,
  onSendToProduction,
  onAdvance,
  onCancel,
  busy,
  dragProps,
}: {
  order: Order
  onAccept: (o: Order) => void
  onReject: (o: Order) => void
  onUploadArt: (o: Order) => void
  onApproveArt: (o: Order) => void
  onSendToProduction: (o: Order) => void
  onAdvance: (o: Order) => void
  onCancel: (o: Order) => void
  busy: boolean
  dragProps?: React.HTMLAttributes<HTMLDivElement> & { draggable?: boolean }
}) {
  const next = nextStatus(order.status, order.fulfillment)
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
          {new Date(order.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
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
            {it.madeToOrder && <span className="ml-1 text-[10px] uppercase text-amber-600">· encomenda</span>}
            {it.customText && <span className="block italic">Texto: “{it.customText}”</span>}
          </li>
        ))}
      </ul>
      {schedule && <p className="text-xs font-medium text-muted-foreground">📅 {schedule}</p>}
      {order.fulfillment === 'entrega' && order.deliveryAddress && (
        <p className="text-xs text-muted-foreground">{order.deliveryAddress}</p>
      )}
      {/* PROVA DE ARTE (ESCAPADA 8.15): mostra a arte enviada + se já foi aprovada. */}
      {order.artUrl && (
        <p className="text-xs">
          <a href={order.artUrl} target="_blank" rel="noopener noreferrer" className="text-primary underline">
            Ver arte
          </a>
          {order.artApproved ? (
            <Badge variant="success" className="ml-2">arte aprovada</Badge>
          ) : (
            <Badge variant="warning" className="ml-2">arte pendente</Badge>
          )}
        </p>
      )}
      <p className="text-sm font-semibold tabular-nums">{formatBrl(order.totalCents)}</p>
      <div className="flex flex-wrap gap-1 pt-1">
        {order.status === 'aguardando' ? (
          <>
            <Button className="h-7 flex-1 px-2 text-xs" disabled={busy} onClick={() => onAccept(order)}>
              Aceitar
            </Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={busy} onClick={() => onReject(order)}>
              Recusar
            </Button>
          </>
        ) : order.status === 'aceito' ? (
          <>
            <Button className="h-7 flex-1 px-2 text-xs" disabled={busy} onClick={() => onUploadArt(order)}>
              Subir arte
            </Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={busy} onClick={() => onCancel(order)}>
              Cancelar
            </Button>
          </>
        ) : order.status === 'arte_aprovacao' ? (
          <>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={busy} onClick={() => onUploadArt(order)}>
              Reenviar arte
            </Button>
            {!order.artApproved && (
              <Button className="h-7 px-2 text-xs" disabled={busy} onClick={() => onApproveArt(order)}>
                Marcar arte aprovada
              </Button>
            )}
            <Button
              className="h-7 px-2 text-xs"
              disabled={busy || !order.artApproved}
              title={!order.artApproved ? 'Aprove a arte antes de produzir.' : undefined}
              onClick={() => onSendToProduction(order)}
            >
              Enviar pra produção
            </Button>
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={busy} onClick={() => onCancel(order)}>
              Cancelar
            </Button>
          </>
        ) : (
          <>
            {next && (
              <Button className="h-7 flex-1 px-2 text-xs" disabled={busy} onClick={() => onAdvance(order)}>
                Avançar → {STATUS_LABEL[next]}
              </Button>
            )}
            <Button variant="outline" className="h-7 px-2 text-xs" disabled={busy} onClick={() => onCancel(order)}>
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
 * Kanban de pedidos do PapelariaBot. Colunas: Aguardando aceite → Aceito → Aprovação de arte → Em
 * produção → Pronto → Saiu pra entrega. Gate de aceite: em "aguardando" o lojista Aceita (→ aceito)
 * ou Recusa (Modal com motivo OPCIONAL → status recusado). PROVA DE ARTE (ESCAPADA 8.15): em
 * "aceito" sobe a arte (artUrl → arte_aprovacao); em "arte_aprovacao" marca a arte aprovada e só
 * então envia pra produção. FUNIL DIVERGE em "pronto": retirada → "retirado" (terminal); entrega →
 * "saiu_entrega" → "entregue". Nas demais colunas: Avançar / Cancelar (confirmado via AlertDialog).
 * Retirados/entregues/recusados/cancelados ficam na aba Histórico (recusado mostra o motivo).
 * Atualiza a cada 30s.
 */
export default function PapelariaOrdersPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'andamento' | 'historico'>('andamento')
  const [cancelTarget, setCancelTarget] = useState<Order | null>(null)
  const [rejectTarget, setRejectTarget] = useState<Order | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [artTarget, setArtTarget] = useState<Order | null>(null)
  const [artUrlInput, setArtUrlInput] = useState('')
  const [artError, setArtError] = useState<string | null>(null)

  // Em andamento: busca tudo e separa client-side pelas colunas (poucos pedidos ativos).
  const active = useQuery({
    queryKey: ['papelaria-orders', 'active'],
    queryFn: () => listOrders({ pageSize: 100 }),
    refetchInterval: 30_000,
  })

  const history = useQuery({
    queryKey: ['papelaria-orders', 'history'],
    queryFn: () => listOrders({ pageSize: 100 }),
    enabled: tab === 'historico',
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status, reason }: { id: string; status: OrderStatus; reason?: string }) =>
      updateOrderStatus(id, status, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['papelaria-orders'] }),
  })

  const approveMutation = useMutation({
    mutationFn: (id: string) => approveArt(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['papelaria-orders'] }),
  })

  // Subir arte: grava a artUrl — o backend (setArtUrl) JÁ move o pedido de 'aceito' pra
  // 'arte_aprovacao' na mesma operação (status = 'arte_aprovacao'). Não encadear updateOrderStatus
  // aqui (seria uma 2ª transição arte_aprovacao→arte_aprovacao = 409 invalid_status_transition).
  const artMutation = useMutation({
    mutationFn: async ({ order, url }: { order: Order; url: string }) => {
      await setArtUrl(order.id, url)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['papelaria-orders'] })
      setArtTarget(null)
      setArtUrlInput('')
      setArtError(null)
    },
    onError: () => setArtError('Erro ao subir a arte.'),
  })

  function accept(o: Order) {
    statusMutation.mutate({ id: o.id, status: 'aceito' })
  }

  function advance(o: Order) {
    const next = nextStatus(o.status, o.fulfillment)
    if (next) statusMutation.mutate({ id: o.id, status: next })
  }

  function sendToProduction(o: Order) {
    statusMutation.mutate({ id: o.id, status: 'em_producao' })
  }

  const allActiveForDnd = active.data?.items ?? []

  // Drag-and-drop: soltar um card numa coluna avança o pedido SÓ nas transições "simples" e válidas.
  // Bloqueios da ESCAPADA da arte: NÃO se arrasta pra 'aceito'/'recusado' (gate de aceite = botão),
  // nem pra 'arte_aprovacao' (exige SUBIR a arte, que pede URL via modal). Pra 'em_producao' só com
  // a arte aprovada (mesmo gate do backend → 409 art_not_approved). As demais: o nextStatus do funil.
  const dnd = useKanbanDnd({
    canDrop: (id, target) => {
      const o = allActiveForDnd.find((x) => x.id === id)
      if (!o || busy) return false
      if (o.status === 'aguardando') return false // aceitar/recusar é gate de botão
      if (o.status === 'aceito') return false // subir arte exige URL (modal)
      if (target === 'arte_aprovacao') return false // idem
      if (target === 'em_producao') {
        // de 'aceito' (sem arte) ou de 'arte_aprovacao' com arte aprovada
        return o.status === 'arte_aprovacao' && o.artApproved
      }
      return nextStatus(o.status, o.fulfillment) === target
    },
    onDrop: (id, target) => {
      statusMutation.mutate({ id, status: target as OrderStatus })
    },
  })

  function openArt(o: Order) {
    setArtUrlInput(o.artUrl ?? '')
    setArtError(null)
    setArtTarget(o)
  }

  function confirmArt() {
    if (!artTarget) return
    const url = artUrlInput.trim()
    if (!url) {
      setArtError('Informe a URL da arte.')
      return
    }
    artMutation.mutate({ order: artTarget, url })
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

  const busy = statusMutation.isPending || approveMutation.isPending || artMutation.isPending

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
      <PageHeader title="Pedidos" description="Aceite ou recuse novos pedidos, gerencie a aprovação da arte e acompanhe a produção, a retirada e a entrega." />

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
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
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
                          onAccept={accept}
                          onReject={(ord) => { setRejectReason(''); setRejectTarget(ord) }}
                          onUploadArt={openArt}
                          onApproveArt={(ord) => approveMutation.mutate(ord.id)}
                          onSendToProduction={sendToProduction}
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
                    <span className="font-mono text-xs text-muted-foreground">#{o.id.slice(0, 8)}</span>
                    <span className="min-w-0 flex-1 truncate">{o.contactName ?? 'Cliente'} · {itemsSummary(o)}</span>
                    <Badge variant={o.fulfillment === 'entrega' ? 'info' : 'muted'}>
                      {fulfillmentLabel(o.fulfillment)}
                    </Badge>
                    <span className="tabular-nums">{formatBrl(o.totalCents)}</span>
                    <Badge variant={o.status === 'retirado' || o.status === 'entregue' ? 'success' : 'danger'}>
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
        onClose={() => { setRejectTarget(null); setRejectReason('') }}
        title="Recusar pedido?"
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            O cliente será notificado da recusa. O motivo é opcional e, se informado, é enviado ao cliente.
          </p>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">Motivo (opcional)</label>
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
            <Button type="button" variant="outline" onClick={() => { setRejectTarget(null); setRejectReason('') }}>
              Voltar
            </Button>
            <Button variant="destructive" disabled={statusMutation.isPending} onClick={confirmReject}>
              {statusMutation.isPending ? 'Recusando…' : 'Recusar pedido'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Subir arte (PROVA DE ARTE — ESCAPADA 8.15): cola a URL da arte; mover pra arte_aprovacao é
          encadeado. Colar uma nova arte ZERA a aprovação (o backend trata). */}
      <Modal
        open={artTarget !== null}
        onClose={() => { setArtTarget(null); setArtUrlInput(''); setArtError(null) }}
        title="Subir arte do pedido"
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Cole a URL da arte (link de imagem/PDF). O pedido vai para a aprovação da arte; ao reenviar uma
            arte, a aprovação anterior é zerada.
          </p>
          <div>
            <label className="mb-1 block text-xs font-medium text-muted-foreground">URL da arte</label>
            <input
              value={artUrlInput}
              onChange={(e) => setArtUrlInput(e.target.value)}
              placeholder="https://…"
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {artError && <p className="text-sm text-destructive">{artError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => { setArtTarget(null); setArtUrlInput(''); setArtError(null) }}>
              Voltar
            </Button>
            <Button disabled={artMutation.isPending} onClick={confirmArt}>
              {artMutation.isPending ? 'Enviando…' : 'Subir arte'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
