import { apiFetch } from '@/lib/api/client'
import type { Plan } from '@/profiles/academia/academia-types'

export type CreatePlanInput = {
  name: string
  monthlyCents: number
  description?: string | null
}

export type UpdatePlanInput = Partial<CreatePlanInput> & { active?: boolean }

export function listPlans(opts: { onlyActive?: boolean } = {}): Promise<{ items: Plan[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: Plan[] }>(`/api/academia/plans${qs}`)
}

export function createPlan(input: CreatePlanInput): Promise<Plan> {
  return apiFetch<Plan>('/api/academia/plans', { method: 'POST', body: JSON.stringify(input) })
}

export function updatePlan(id: string, input: UpdatePlanInput): Promise<Plan> {
  return apiFetch<Plan>(`/api/academia/plans/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function togglePlan(id: string, active: boolean): Promise<Plan> {
  return apiFetch<Plan>(`/api/academia/plans/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deletePlan(id: string): Promise<void> {
  return apiFetch<void>(`/api/academia/plans/${id}`, { method: 'DELETE' })
}
