/**
 * Status de uma matrícula do perfil escola (camada 8.19) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/escola/EscolaEnrollmentStatus.java.
 *
 * O EscolaEnrollmentStatusParityTest (backend) garante que os ids nunca divergem. A CHECK
 * constraint de escola_enrollments.status trava os mesmos ids.
 *
 * Transições (clone do academia):
 *   ativa     → suspensa, cancelada
 *   suspensa  → ativa, cancelada
 *   cancelada → (terminal)
 */
export const ESCOLA_ENROLLMENT_STATUSES = [
  { id: 'ativa', label: 'Ativa' },
  { id: 'suspensa', label: 'Suspensa' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type EscolaEnrollmentStatus = (typeof ESCOLA_ENROLLMENT_STATUSES)[number]
export type EscolaEnrollmentStatusId = EscolaEnrollmentStatus['id']

/** Transições permitidas a partir de cada status (espelha EscolaEnrollmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<EscolaEnrollmentStatusId, EscolaEnrollmentStatusId[]> = {
  ativa: ['suspensa', 'cancelada'],
  suspensa: ['ativa', 'cancelada'],
  cancelada: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return ESCOLA_ENROLLMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
