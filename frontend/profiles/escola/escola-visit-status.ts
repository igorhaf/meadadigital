/**
 * Status de uma visita do perfil escola (camada 8.19) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/escola/EscolaVisitStatus.java.
 *
 * O EscolaVisitStatusParityTest (backend) garante que os ids nunca divergem. A CHECK
 * constraint de escola_visits.status trava os mesmos ids.
 *
 * Transições:
 *   agendada  → realizada, cancelada
 *   realizada → (terminal)
 *   cancelada → (terminal)
 */
export const ESCOLA_VISIT_STATUSES = [
  { id: 'agendada', label: 'Agendada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type EscolaVisitStatus = (typeof ESCOLA_VISIT_STATUSES)[number]
export type EscolaVisitStatusId = EscolaVisitStatus['id']

/** Transições permitidas a partir de cada status (espelha EscolaVisitStatus.allowedNext). */
export const ALLOWED_NEXT: Record<EscolaVisitStatusId, EscolaVisitStatusId[]> = {
  agendada: ['realizada', 'cancelada'],
  realizada: [],
  cancelada: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return ESCOLA_VISIT_STATUSES.find((s) => s.id === id)?.label ?? id
}
