import { apiFetch } from '@/lib/api/client'
import type { EventConfig } from '@/profiles/eventos/eventos-types'

export type UpdateConfigInput = {
  businessName?: string | null
  notes?: string | null
  autoCompleteEnabled: boolean
  postEventEnabled: boolean
  reviewLink?: string | null
  followUpEnabled: boolean
  followUpDays: number
}

export function getConfig(): Promise<EventConfig> {
  return apiFetch<EventConfig>('/api/eventos/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<EventConfig> {
  return apiFetch<EventConfig>('/api/eventos/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
