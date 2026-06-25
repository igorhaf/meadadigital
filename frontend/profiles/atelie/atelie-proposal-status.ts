/**
 * Status de uma proposta de ateliê do perfil atelie (camada 8.14) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/atelie/AtelieProposalStatus.java.
 *
 * O AtelieProposalStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de atelie_proposals.status trava os mesmos ids.
 *
 * Transições (funil de ateliê sob medida — clone do funil de eventos):
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada/recusada/cancelada → (terminal)
 */
export const ATELIE_PROPOSAL_STATUSES = [
  { id: 'rascunho', label: 'Rascunho' },
  { id: 'orcada', label: 'Orçada' },
  { id: 'aprovada', label: 'Aprovada' },
  { id: 'recusada', label: 'Recusada' },
  { id: 'fechada', label: 'Fechada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type AtelieProposalStatus = (typeof ATELIE_PROPOSAL_STATUSES)[number]
export type AtelieProposalStatusId = AtelieProposalStatus['id']

/** Transições permitidas a partir de cada status (espelha AtelieProposalStatus.allowedNext). */
export const ALLOWED_NEXT: Record<AtelieProposalStatusId, AtelieProposalStatusId[]> = {
  rascunho: ['orcada', 'cancelada'],
  orcada: ['aprovada', 'recusada', 'cancelada'],
  aprovada: ['fechada', 'cancelada'],
  fechada: ['realizada', 'cancelada'],
  realizada: [],
  recusada: [],
  cancelada: [],
}

/**
 * Estados em que os ITENS da proposta (orçamento E provas/ajustes) não podem mais ser mutados
 * (espelha AtelieProposalStatus.itemsLocked — trava a partir de 'fechada'). O painel desabilita os
 * editores nesses estados.
 */
export const ITEMS_LOCKED: Record<AtelieProposalStatusId, boolean> = {
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
  return ATELIE_PROPOSAL_STATUSES.find((s) => s.id === id)?.label ?? id
}
