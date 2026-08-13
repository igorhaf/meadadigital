import { apiFetch } from './client'

/**
 * Janela de disponibilidade do tenant (camada 5.17 #61) — shape do /admin/availability-slots.
 * weekday 0=domingo..6=sábado. Horários como "HH:MM". Os slots concretos são derivados no
 * backend a partir de [startsAt, endsAt) em passos de slotMinutes.
 */
export type AvailabilitySlot = {
  id: string
  weekday: number
  startsAt: string
  endsAt: string
  slotMinutes: number
  active: boolean
}

/** Payload de criação/edição (sem id; na edição, inclui active). */
export type AvailabilitySlotPayload = {
  weekday: number
  startsAt: string
  endsAt: string
  slotMinutes: number
  active?: boolean
}

/** Lista todas as janelas da empresa do admin (inclui inativas — tela de gestão). */
export async function getMyAvailabilitySlots(): Promise<AvailabilitySlot[]> {
  return apiFetch<AvailabilitySlot[]>('/admin/availability-slots')
}

/** Cria uma janela. Retorna a janela criada (com id). */
export async function createAvailabilitySlot(
  payload: AvailabilitySlotPayload,
): Promise<AvailabilitySlot> {
  return apiFetch<AvailabilitySlot>('/admin/availability-slots', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** Atualiza uma janela (id pela URL). 200 em sucesso; 404 se não for da empresa. */
export async function updateAvailabilitySlot(
  id: string,
  payload: AvailabilitySlotPayload,
): Promise<void> {
  return apiFetch<void>(`/admin/availability-slots/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

/** Remove uma janela. 204 No Content. */
export async function deleteAvailabilitySlot(id: string): Promise<void> {
  return apiFetch<void>(`/admin/availability-slots/${id}`, { method: 'DELETE' })
}
