/**
 * Forma de entrega de um pedido padaria (camada 8.8, ESCAPADA) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/padaria/PadariaFulfillment.java.
 *
 * O pedido é RETIRADA (o cliente busca na loja) ou ENTREGA (delivery no endereço). O fluxo do
 * Kanban diverge no fim por causa disso: pronto → retirado (retirada) ou pronto → saiu_entrega →
 * entregue (entrega).
 * O PadariaFulfillmentParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK de padaria_orders.fulfillment trava os mesmos ids no banco.
 */
export const PADARIA_FULFILLMENTS = [
  { id: 'retirada', label: 'Retirada' },
  { id: 'entrega', label: 'Entrega' },
] as const

export type PadariaFulfillment = (typeof PADARIA_FULFILLMENTS)[number]
export type PadariaFulfillmentId = PadariaFulfillment['id']

/** Rótulo pt-BR de uma forma de entrega (fallback: o próprio id se desconhecido). */
export function fulfillmentLabel(id: string): string {
  return PADARIA_FULFILLMENTS.find((f) => f.id === id)?.label ?? id
}
