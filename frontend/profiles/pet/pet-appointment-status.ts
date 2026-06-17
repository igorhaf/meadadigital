/**
 * Status de um agendamento do perfil pet (camada 7.8) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/pet/PetAppointmentStatus.java.
 *
 * O PetAppointmentStatusParityTest (backend) garante que os ids nunca divergem. A CHECK constraint
 * de pet_appointments.status (migration 37) trava os mesmos ids.
 *
 * Transições (decisão 2):
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado/cancelado/falta → (terminal)
 */
export const PET_APPOINTMENT_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'falta', label: 'Falta' },
] as const

export type PetAppointmentStatus = (typeof PET_APPOINTMENT_STATUSES)[number]
export type PetAppointmentStatusId = PetAppointmentStatus['id']

/** Transições permitidas a partir de cada status (espelha PetAppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<PetAppointmentStatusId, PetAppointmentStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'falta'],
  realizado: [],
  cancelado: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return PET_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
