import { apiFetch } from '@/lib/api/client'
import type { AestheticPackageStatusId } from '@/profiles/estetica/aesthetic-package-status'
import type { AestheticPackage } from '@/profiles/estetica/estetica-types'

type PackagePage = { items: AestheticPackage[]; total: number; page: number; pageSize: number }

export type CreatePackageInput = {
  contactId?: string | null
  customerName?: string | null
  procedureId: string
  totalSessions: number
  notes?: string | null
}

export function listPackages(
  opts: {
    status?: string
    contactId?: string
    procedureId?: string
    page?: number
    pageSize?: number
  } = {},
): Promise<PackagePage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.contactId) p.set('contactId', opts.contactId)
  if (opts.procedureId) p.set('procedureId', opts.procedureId)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<PackagePage>(`/api/estetica/packages${qs ? `?${qs}` : ''}`)
}

export function getPackage(id: string): Promise<AestheticPackage> {
  return apiFetch<AestheticPackage>(`/api/estetica/packages/${id}`)
}

export function createPackage(input: CreatePackageInput): Promise<AestheticPackage> {
  return apiFetch<AestheticPackage>('/api/estetica/packages', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePackageStatus(
  id: string,
  newStatus: AestheticPackageStatusId,
): Promise<AestheticPackage> {
  return apiFetch<AestheticPackage>(`/api/estetica/packages/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
