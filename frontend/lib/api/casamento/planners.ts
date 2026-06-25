import { apiFetch } from '@/lib/api/client'
import type { WeddingPlanner } from '@/profiles/casamento/casamento-types'

export type CreatePlannerInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdatePlannerInput = Partial<CreatePlannerInput> & { active?: boolean }

export function listPlanners(opts: { onlyActive?: boolean } = {}): Promise<{ items: WeddingPlanner[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: WeddingPlanner[] }>(`/api/casamento/planners${qs}`)
}

export function getPlanner(id: string): Promise<WeddingPlanner> {
  return apiFetch<WeddingPlanner>(`/api/casamento/planners/${id}`)
}

export function createPlanner(input: CreatePlannerInput): Promise<WeddingPlanner> {
  return apiFetch<WeddingPlanner>('/api/casamento/planners', { method: 'POST', body: JSON.stringify(input) })
}

export function updatePlanner(id: string, input: UpdatePlannerInput): Promise<WeddingPlanner> {
  return apiFetch<WeddingPlanner>(`/api/casamento/planners/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function togglePlanner(id: string, active: boolean): Promise<WeddingPlanner> {
  return apiFetch<WeddingPlanner>(`/api/casamento/planners/${id}/toggle`, {
    method: 'PATCH', body: JSON.stringify({ active }),
  })
}

export function deletePlanner(id: string): Promise<void> {
  return apiFetch<void>(`/api/casamento/planners/${id}`, { method: 'DELETE' })
}
