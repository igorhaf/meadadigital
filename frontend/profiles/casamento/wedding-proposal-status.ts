/**
 * Status de uma proposta de casamento do perfil casamento (camada 8.7) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/casamento/WeddingProposalStatus.java.
 *
 * O WeddingProposalStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de wedding_proposals.status trava os mesmos ids.
 *
 * Transições (funil de assessoria de casamento — clone do funil de eventos):
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada/recusada/cancelada → (terminal)
 */
export const WEDDING_PROPOSAL_STATUSES = [
  { id: 'rascunho', label: 'Rascunho' },
  { id: 'orcada', label: 'Orçada' },
  { id: 'aprovada', label: 'Aprovada' },
  { id: 'recusada', label: 'Recusada' },
  { id: 'fechada', label: 'Fechada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type WeddingProposalStatus = (typeof WEDDING_PROPOSAL_STATUSES)[number]
export type WeddingProposalStatusId = WeddingProposalStatus['id']

/** Transições permitidas a partir de cada status (espelha WeddingProposalStatus.allowedNext). */
export const ALLOWED_NEXT: Record<WeddingProposalStatusId, WeddingProposalStatusId[]> = {
  rascunho: ['orcada', 'cancelada'],
  orcada: ['aprovada', 'recusada', 'cancelada'],
  aprovada: ['fechada', 'cancelada'],
  fechada: ['realizada', 'cancelada'],
  realizada: [],
  recusada: [],
  cancelada: [],
}

/**
 * Estados em que os ITENS da proposta (orçamento, cronograma E checklist) não podem mais ser mutados
 * (espelha WeddingProposalStatus.itemsLocked — trava a partir de 'fechada'). O painel desabilita os
 * editores nesses estados.
 */
export const ITEMS_LOCKED: Record<WeddingProposalStatusId, boolean> = {
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
  return WEDDING_PROPOSAL_STATUSES.find((s) => s.id === id)?.label ?? id
}
