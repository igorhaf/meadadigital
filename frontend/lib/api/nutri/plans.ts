import { apiFetch } from '@/lib/api/client'
import type { NutriPlan } from '@/profiles/nutri/nutri-types'

export type CreatePlanInput = {
  patientId: string
  professionalId?: string | null
  title: string
  body: string
  startsOn?: string | null // yyyy-MM-dd
  endsOn?: string | null
  active?: boolean
  notes?: string | null
}

export type UpdatePlanInput = {
  title?: string
  body?: string
  professionalId?: string | null
  clearProfessional?: boolean
  startsOn?: string | null
  clearStarts?: boolean
  endsOn?: string | null
  clearEnds?: boolean
  notes?: string | null
}

export function listPlans(
  patientId: string,
  opts: { status?: string } = {},
): Promise<{ items: NutriPlan[] }> {
  const p = new URLSearchParams()
  p.set('patientId', patientId)
  if (opts.status) p.set('status', opts.status)
  return apiFetch<{ items: NutriPlan[] }>(`/api/nutri/plans?${p.toString()}`)
}

export function getPlan(id: string): Promise<NutriPlan> {
  return apiFetch<NutriPlan>(`/api/nutri/plans/${id}`)
}

export function getActivePlan(patientId: string): Promise<NutriPlan> {
  return apiFetch<NutriPlan>(`/api/nutri/plans/active?patientId=${patientId}`)
}

export function createPlan(input: CreatePlanInput): Promise<NutriPlan> {
  return apiFetch<NutriPlan>('/api/nutri/plans', { method: 'POST', body: JSON.stringify(input) })
}

export function updatePlan(id: string, input: UpdatePlanInput): Promise<NutriPlan> {
  return apiFetch<NutriPlan>(`/api/nutri/plans/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function archivePlan(id: string): Promise<NutriPlan> {
  return apiFetch<NutriPlan>(`/api/nutri/plans/${id}/archive`, { method: 'PATCH' })
}

export function activatePlan(id: string): Promise<NutriPlan> {
  return apiFetch<NutriPlan>(`/api/nutri/plans/${id}/activate`, { method: 'PATCH' })
}
