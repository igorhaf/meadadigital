import { apiFetch } from './client'

/**
 * Agendamento do tenant (camada 5.19 #59) — shape do /admin/appointments. scheduledAt em
 * ISO-8601 (UTC). status do ciclo de vida; serviceId/conversationId/notes nullable.
 */
export type Appointment = {
  id: string
  contactId: string
  conversationId: string | null
  serviceId: string | null
  scheduledAt: string
  status: 'scheduled' | 'completed' | 'cancelled' | 'no_show'
  notes: string | null
}

/**
 * Lista os agendamentos da empresa no range [fromIso, toIso). Sem from/to → o backend usa o
 * mês corrente (fuso do tenant). Datas em ISO-8601 (ex.: "2026-06-01T00:00:00Z").
 */
export async function getAppointments(fromIso?: string, toIso?: string): Promise<Appointment[]> {
  const qs = fromIso && toIso ? `?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}` : ''
  return apiFetch<Appointment[]>(`/admin/appointments${qs}`)
}

/** Atualiza o status de um agendamento (concluído/cancelado/no-show). 200 em sucesso. */
export async function updateAppointmentStatus(
  id: string,
  status: Appointment['status'],
): Promise<void> {
  return apiFetch<void>(`/admin/appointments/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}
