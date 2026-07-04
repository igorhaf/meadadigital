import { apiFetch } from '@/lib/api/client'
import type { Order, OrderStatus } from '@/profiles/papelaria/papelaria-types'

type OrderPage = { items: Order[]; total: number; page: number; pageSize: number }

export function listOrders(
  opts: { status?: string; page?: number; pageSize?: number } = {},
): Promise<OrderPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<OrderPage>(`/api/papelaria/orders${qs ? `?${qs}` : ''}`)
}

export function getOrder(id: string): Promise<Order> {
  return apiFetch<Order>(`/api/papelaria/orders/${id}`)
}

/**
 * Muda o status de um pedido. rejectionReason é opcional e só faz sentido com newStatus =
 * 'recusado' (gate de aceite); para os demais status fica undefined e o backend ignora.
 * A aprovação da arte (arte_aprovacao → em_producao) também passa por aqui; o backend rejeita o
 * avanço se artApproved ainda for false (gate da PROVA DE ARTE — ESCAPADA 8.15).
 */
export function updateOrderStatus(
  id: string,
  newStatus: OrderStatus,
  rejectionReason?: string,
): Promise<Order> {
  return apiFetch<Order>(`/api/papelaria/orders/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus, rejectionReason }),
  })
}

/**
 * PROVA DE ARTE (ESCAPADA 8.15): sobe a URL da arte do pedido. Mover o pedido pra arte_aprovacao
 * (aguardando o cliente aprovar) é feito definindo a artUrl + um updateOrderStatus pra
 * arte_aprovacao na tela. Colar uma artUrl ZERA artApproved (nova arte exige nova aprovação).
 */
export function setArtUrl(id: string, artUrl: string): Promise<Order> {
  return apiFetch<Order>(`/api/papelaria/orders/${id}/art`, {
    method: 'PATCH',
    body: JSON.stringify({ artUrl }),
  })
}

/** Marca a arte do pedido como aprovada (artApproved = true) — libera o avanço pra em_producao.
 * O backend expõe a aprovação no MESMO endpoint /art, via { approve: true } (não há /art/approve). */
export function approveArt(id: string): Promise<Order> {
  return apiFetch<Order>(`/api/papelaria/orders/${id}/art`, {
    method: 'PATCH',
    body: JSON.stringify({ approve: true }),
  })
}

export type DepositInput = {
  depositCents: number | null
  depositPaid: boolean
}

/** Registra o sinal/entrada e/ou marca como recebido (onda #1 — manual até o gateway #50). */
export function updateDeposit(id: string, input: DepositInput): Promise<Order> {
  return apiFetch<Order>(`/api/papelaria/orders/${id}/deposit`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
