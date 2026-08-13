import { apiFetch } from '@/lib/api/client'

/** Plano na visão do super-admin (camada 6.8). Limites null = ilimitado. */
export type Plan = {
  id: string
  name: string
  slug: string
  monthlyPriceCents: number
  maxAdmins: number | null
  maxFaqs: number | null
  maxConversationsMonth: number | null
  maxUsers: number | null
  features: Record<string, unknown> | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export type CreatePlanInput = {
  name: string
  slug: string
  monthlyPriceCents?: number
  maxAdmins?: number | null
  maxFaqs?: number | null
  maxConversationsMonth?: number | null
  maxUsers?: number | null
  features?: Record<string, unknown> | null
}

export type UpdatePlanInput = Partial<CreatePlanInput> & { active?: boolean }

export function listPlans(): Promise<{ items: Plan[] }> {
  return apiFetch<{ items: Plan[] }>('/admin/plans')
}

export function createPlan(input: CreatePlanInput): Promise<Plan> {
  return apiFetch<Plan>('/admin/plans', { method: 'POST', body: JSON.stringify(input) })
}

export function updatePlan(id: string, input: UpdatePlanInput): Promise<Plan> {
  return apiFetch<Plan>(`/admin/plans/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

/** Soft delete (active=false). */
export function deletePlan(id: string): Promise<void> {
  return apiFetch<void>(`/admin/plans/${id}`, { method: 'DELETE' })
}
