/**
 * Status de uma reserva do perfil pousada (camada 7.6) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/pousada/PousadaReservationStatus.java.
 *
 * O PousadaReservationStatusParityTest (backend) garante que os ids nunca divergem. A CHECK
 * constraint de pousada_reservations.status (migration 35) trava os mesmos ids.
 *
 * Transições (decisão 2):
 *   reservado   → confirmado, cancelado
 *   confirmado  → checked_in, cancelado, no_show
 *   checked_in  → checked_out
 *   checked_out/cancelado/no_show → (terminal)
 */
export const POUSADA_RESERVATION_STATUSES = [
  { id: 'reservado', label: 'Reservado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'checked_in', label: 'Check-in feito' },
  { id: 'checked_out', label: 'Check-out feito' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'no_show', label: 'Não compareceu' },
] as const

export type PousadaReservationStatus = (typeof POUSADA_RESERVATION_STATUSES)[number]
export type PousadaReservationStatusId = PousadaReservationStatus['id']

/** Transições permitidas a partir de cada status (espelha PousadaReservationStatus.allowedNext). */
export const ALLOWED_NEXT: Record<PousadaReservationStatusId, PousadaReservationStatusId[]> = {
  reservado: ['confirmado', 'cancelado'],
  confirmado: ['checked_in', 'cancelado', 'no_show'],
  checked_in: ['checked_out'],
  checked_out: [],
  cancelado: [],
  no_show: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return POUSADA_RESERVATION_STATUSES.find((s) => s.id === id)?.label ?? id
}
