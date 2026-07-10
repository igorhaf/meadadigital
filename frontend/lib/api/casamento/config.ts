import { apiFetch } from '@/lib/api/client'
import type { WeddingConfig } from '@/profiles/casamento/casamento-types'

export type UpdateConfigInput = {
  businessName?: string | null
  notes?: string | null
  checklistReminderEnabled?: boolean
  paymentReminderEnabled?: boolean
  autoCompleteEnabled?: boolean
  anniversaryEnabled?: boolean
  postEventEnabled: boolean
  reviewLink?: string | null
  followUpEnabled: boolean
  followUpDays: number
}

export function getConfig(): Promise<WeddingConfig> {
  return apiFetch<WeddingConfig>('/api/casamento/config')
}

export function updateConfig(input: UpdateConfigInput): Promise<WeddingConfig> {
  return apiFetch<WeddingConfig>('/api/casamento/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
