import { apiFetch } from '@/lib/api/client'
import type { YieldReference } from '@/profiles/las/las-types'

export type CreateYieldInput = {
  pieceType: string
  yarnSpec?: string | null
  skeins: number
  notes?: string | null
  active?: boolean
}
export type UpdateYieldInput = Partial<CreateYieldInput>

export function listYield(): Promise<{ items: YieldReference[] }> {
  return apiFetch<{ items: YieldReference[] }>('/api/las/yield')
}

export function createYield(input: CreateYieldInput): Promise<YieldReference> {
  return apiFetch<YieldReference>('/api/las/yield', { method: 'POST', body: JSON.stringify(input) })
}

export function updateYield(id: string, input: UpdateYieldInput): Promise<YieldReference> {
  return apiFetch<YieldReference>(`/api/las/yield/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteYield(id: string): Promise<void> {
  return apiFetch<void>(`/api/las/yield/${id}`, { method: 'DELETE' })
}
