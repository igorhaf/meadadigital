import { apiFetch } from '@/lib/api/client'
import type { EventPlanner } from '@/profiles/eventos/eventos-types'

export type CreatePlannerInput = {
  name: string
  specialty?: string | null
  notes?: string | null
}

export type UpdatePlannerInput = Partial<CreatePlannerInput> & { active?: boolean }

export function listPlanners(
  opts: { onlyActive?: boolean } = {},
): Promise<{ items: EventPlanner[] }> {
  const qs = opts.onlyActive ? '?onlyActive=true' : ''
  return apiFetch<{ items: EventPlanner[] }>(`/api/eventos/planners${qs}`)
}

export function getPlanner(id: string): Promise<EventPlanner> {
  return apiFetch<EventPlanner>(`/api/eventos/planners/${id}`)
}

export function createPlanner(input: CreatePlannerInput): Promise<EventPlanner> {
  return apiFetch<EventPlanner>('/api/eventos/planners', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePlanner(id: string, input: UpdatePlannerInput): Promise<EventPlanner> {
  return apiFetch<EventPlanner>(`/api/eventos/planners/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function togglePlanner(id: string, active: boolean): Promise<EventPlanner> {
  return apiFetch<EventPlanner>(`/api/eventos/planners/${id}/toggle`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export function deletePlanner(id: string): Promise<void> {
  return apiFetch<void>(`/api/eventos/planners/${id}`, { method: 'DELETE' })
}
