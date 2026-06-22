/**
 * Status de um agendamento de estética (camada 8.3) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/estetica/AestheticAppointmentStatus.java.
 *
 * O AestheticAppointmentStatusParityTest (backend) garante a paridade. Transições:
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado/cancelado/falta → (terminal)
 */
export const AESTHETIC_APPOINTMENT_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'falta', label: 'Falta' },
] as const

export type AestheticAppointmentStatus = (typeof AESTHETIC_APPOINTMENT_STATUSES)[number]
export type AestheticAppointmentStatusId = AestheticAppointmentStatus['id']

export const ALLOWED_NEXT: Record<AestheticAppointmentStatusId, AestheticAppointmentStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'falta'],
  realizado: [],
  cancelado: [],
  falta: [],
}

export function statusLabel(id: string): string {
  return AESTHETIC_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
