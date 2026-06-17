/**
 * Status de uma matrícula do perfil academia (camada 7.7) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/academia/AcademiaMembershipStatus.java.
 *
 * O AcademiaMembershipStatusParityTest (backend) garante que os ids nunca divergem. A CHECK
 * constraint de academia_memberships.status (migration 36) trava os mesmos ids.
 *
 * Transições (decisão 2):
 *   ativa     → suspensa, cancelada
 *   suspensa  → ativa, cancelada
 *   cancelada → (terminal)
 */
export const ACADEMIA_MEMBERSHIP_STATUSES = [
  { id: 'ativa', label: 'Ativa' },
  { id: 'suspensa', label: 'Suspensa' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type AcademiaMembershipStatus = (typeof ACADEMIA_MEMBERSHIP_STATUSES)[number]
export type AcademiaMembershipStatusId = AcademiaMembershipStatus['id']

/** Transições permitidas a partir de cada status (espelha AcademiaMembershipStatus.allowedNext). */
export const ALLOWED_NEXT: Record<AcademiaMembershipStatusId, AcademiaMembershipStatusId[]> = {
  ativa: ['suspensa', 'cancelada'],
  suspensa: ['ativa', 'cancelada'],
  cancelada: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return ACADEMIA_MEMBERSHIP_STATUSES.find((s) => s.id === id)?.label ?? id
}
