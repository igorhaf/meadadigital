/**
 * Status de uma consulta de dermatologia (camada 8.11) — espelho 1:1 de
 * src/main/java/com/meada/profiles/dermatologia/DermatologiaAppointmentStatus.java.
 *
 * O DermatologiaAppointmentStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de dermatologia_appointments.status (migration 55) trava os mesmos
 * ids. Status FEMININO.
 *
 * Transições:
 *   agendada   → confirmada, cancelada
 *   confirmada → realizada, cancelada, falta
 *   realizada/cancelada/falta → (terminal)
 */
export const DERMATOLOGIA_APPOINTMENT_STATUSES = [
  { id: 'agendada', label: 'Agendada' },
  { id: 'confirmada', label: 'Confirmada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
  { id: 'falta', label: 'Falta' },
] as const

export type DermatologiaAppointmentStatus = (typeof DERMATOLOGIA_APPOINTMENT_STATUSES)[number]
export type DermatologiaAppointmentStatusId = DermatologiaAppointmentStatus['id']

/** Transições permitidas a partir de cada status (espelha DermatologiaAppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<
  DermatologiaAppointmentStatusId,
  DermatologiaAppointmentStatusId[]
> = {
  agendada: ['confirmada', 'cancelada'],
  confirmada: ['realizada', 'cancelada', 'falta'],
  realizada: [],
  cancelada: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return DERMATOLOGIA_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
