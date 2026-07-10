import { apiFetch } from '@/lib/api/client'
import type { TestDriveStatusId } from '@/profiles/concessionaria/concessionaria-test-drive-status'
import type { TestDrive } from '@/profiles/concessionaria/concessionaria-types'

type TestDrivePage = { items: TestDrive[]; total: number; page: number; pageSize: number }

export type CreateTestDriveInput = {
  vehicleId: string
  salespersonId: string
  startAt: string // ISO-8601 instant
  customerName?: string | null
  notes?: string | null
}

export function listTestDrives(
  opts: { status?: string; page?: number; pageSize?: number } = {},
): Promise<TestDrivePage> {
  const p = new URLSearchParams()
  if (opts.status) p.set('status', opts.status)
  if (opts.page !== undefined) p.set('page', String(opts.page))
  if (opts.pageSize !== undefined) p.set('pageSize', String(opts.pageSize))
  const qs = p.toString()
  return apiFetch<TestDrivePage>(`/api/concessionaria/test-drives${qs ? `?${qs}` : ''}`)
}

export function getTestDrive(id: string): Promise<TestDrive> {
  return apiFetch<TestDrive>(`/api/concessionaria/test-drives/${id}`)
}

export function createTestDrive(input: CreateTestDriveInput): Promise<TestDrive> {
  return apiFetch<TestDrive>('/api/concessionaria/test-drives', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateTestDriveStatus(
  id: string,
  newStatus: TestDriveStatusId,
): Promise<TestDrive> {
  return apiFetch<TestDrive>(`/api/concessionaria/test-drives/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ newStatus }),
  })
}
