import { apiFetch } from '@/lib/api/client'
import type { LeadStatusId } from '@/profiles/concessionaria/concessionaria-lead-status'
import type { Lead, PaymentCondition } from '@/profiles/concessionaria/concessionaria-types'

type LeadPage = { items: Lead[]; total: number; page: number; pageSize: number }

export type CreateLeadInput = {
  vehicleId: string
  paymentCondition: PaymentCondition
  customerName?: string | null
  customerPhone?: string | null
  salespersonId?: string | null
  notes?: string | null
}

export function listLeads(
  opts: { status?: string; page?: number; pageSize?: number } = {},
): Promise<LeadPage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<LeadPage>(`/api/concessionaria/leads${qs ? `?${qs}` : ''}`)
}

export function getLead(id: string): Promise<Lead> {
  return apiFetch<Lead>(`/api/concessionaria/leads/${id}`)
}

export function createLead(input: CreateLeadInput): Promise<Lead> {
  return apiFetch<Lead>('/api/concessionaria/leads', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateLeadStatus(
  id: string,
  newStatus: LeadStatusId,
  lostReason?: string | null,
): Promise<Lead> {
  return apiFetch<Lead>(`/api/concessionaria/leads/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus, lostReason: lostReason ?? null }),
  })
}

export function assignSalesperson(id: string, salespersonId: string): Promise<Lead> {
  return apiFetch<Lead>(`/api/concessionaria/leads/${id}/salesperson`, {
    method: 'PATCH',
    body: JSON.stringify({ salespersonId }),
  })
}
