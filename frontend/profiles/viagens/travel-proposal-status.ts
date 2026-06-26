/**
 * Status de uma proposta de viagem do perfil viagens (camada 8.18) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/viagens/TravelProposalStatus.java.
 *
 * O TravelProposalStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de viagens_proposals.status (migration 62) trava os mesmos ids.
 *
 * Clona o funil do EventosBot (camada 8.2):
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada/recusada/cancelada → (terminal)
 */
export const TRAVEL_PROPOSAL_STATUSES = [
  { id: 'rascunho', label: 'Rascunho' },
  { id: 'orcada', label: 'Orçada' },
  { id: 'aprovada', label: 'Aprovada' },
  { id: 'recusada', label: 'Recusada' },
  { id: 'fechada', label: 'Fechada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type TravelProposalStatus = (typeof TRAVEL_PROPOSAL_STATUSES)[number]
export type TravelProposalStatusId = TravelProposalStatus['id']

/** Transições permitidas a partir de cada status (espelha TravelProposalStatus.allowedNext). */
export const ALLOWED_NEXT: Record<TravelProposalStatusId, TravelProposalStatusId[]> = {
  rascunho: ['orcada', 'cancelada'],
  orcada: ['aprovada', 'recusada', 'cancelada'],
  aprovada: ['fechada', 'cancelada'],
  fechada: ['realizada', 'cancelada'],
  realizada: [],
  recusada: [],
  cancelada: [],
}

/**
 * Estados em que os ITENS da proposta (cotação E itinerário) não podem mais ser mutados
 * (espelha TravelProposalStatus.itemsLocked — trava a partir de 'fechada'). O painel desabilita os
 * editores nesses estados.
 */
export const ITEMS_LOCKED: Record<TravelProposalStatusId, boolean> = {
  rascunho: false,
  orcada: false,
  aprovada: false,
  fechada: true,
  realizada: true,
  recusada: true,
  cancelada: true,
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return TRAVEL_PROPOSAL_STATUSES.find((s) => s.id === id)?.label ?? id
}
