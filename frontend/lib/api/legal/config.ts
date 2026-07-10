import { apiFetch } from '@/lib/api/client'

export type LegalConfig = {
  companyId: string
  reviewLink: string | null
  postClosureEnabled: boolean
  deadlineReminderEnabled: boolean
}

export type LegalConfigInput = {
  reviewLink?: string | null
  postClosureEnabled: boolean
  deadlineReminderEnabled: boolean
}

export function getConfig(): Promise<LegalConfig> {
  return apiFetch<LegalConfig>('/api/legal/config')
}

export function updateConfig(input: LegalConfigInput): Promise<LegalConfig> {
  return apiFetch<LegalConfig>('/api/legal/config', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
