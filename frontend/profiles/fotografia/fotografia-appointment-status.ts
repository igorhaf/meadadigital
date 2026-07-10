/**
 * Status de uma sessão de fotografia (camada 8.16) — espelho 1:1 de
 * src/main/java/com/meada/profiles/fotografia/FotografiaAppointmentStatus.java.
 *
 * O FotografiaAppointmentStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de fotografia_sessions.status trava os mesmos ids. Status FEMININO.
 *
 * Transições:
 *   agendada   → confirmada, cancelada
 *   confirmada → realizada, cancelada, falta
 *   realizada  → entregue
 *   entregue/cancelada/falta → (terminal)
 */
export const FOTOGRAFIA_APPOINTMENT_STATUSES = [
  { id: 'agendada', label: 'Agendada' },
  { id: 'confirmada', label: 'Confirmada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'cancelada', label: 'Cancelada' },
  { id: 'falta', label: 'Falta' },
] as const

/**
 * União dos ids de status — o FotografiaAppointmentStatusParityTest (Java) lê ESTA união e casa
 * com os ids do enum Java (lowercase, feminino). Os 6 ids precisam aparecer aqui literalmente.
 */
export type FotografiaAppointmentStatusId =
  'agendada' | 'confirmada' | 'realizada' | 'entregue' | 'cancelada' | 'falta'

export type FotografiaAppointmentStatus = (typeof FOTOGRAFIA_APPOINTMENT_STATUSES)[number]

/** Transições permitidas a partir de cada status (espelha FotografiaAppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<FotografiaAppointmentStatusId, FotografiaAppointmentStatusId[]> =
  {
    agendada: ['confirmada', 'cancelada'],
    confirmada: ['realizada', 'cancelada', 'falta'],
    realizada: ['entregue'],
    entregue: [],
    cancelada: [],
    falta: [],
  }

/** Próximo status "natural" do fluxo feliz (agendada→confirmada→realizada→entregue). */
export const NEXT_STATUS: Record<
  FotografiaAppointmentStatusId,
  FotografiaAppointmentStatusId | null
> = {
  agendada: 'confirmada',
  confirmada: 'realizada',
  realizada: 'entregue',
  entregue: null,
  cancelada: null,
  falta: null,
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return FOTOGRAFIA_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
