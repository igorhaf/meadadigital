import { apiFetch } from '@/lib/api/client'
import type { AtelieMeasurement } from '@/profiles/atelie/atelie-types'

export function listMeasurements(contactId: string): Promise<{ items: AtelieMeasurement[] }> {
  return apiFetch<{ items: AtelieMeasurement[] }>(`/api/atelie/contacts/${contactId}/measurements`)
}

/** POST é upsert por (contato, lower(label)) — regravar a mesma medida atualiza o valor. */
export function upsertMeasurement(
  contactId: string,
  input: { label: string; value: string },
): Promise<AtelieMeasurement> {
  return apiFetch<AtelieMeasurement>(`/api/atelie/contacts/${contactId}/measurements`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function deleteMeasurement(id: string): Promise<void> {
  return apiFetch<void>(`/api/atelie/measurements/${id}`, { method: 'DELETE' })
}
