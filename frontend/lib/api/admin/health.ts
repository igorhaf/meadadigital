import { apiFetch } from '@/lib/api/client'

/** Resumo de saúde da plataforma (camada 6.4). */
export type HealthSummary = {
  webhookOff: boolean
  lastHeartbeatAt: string | null
  heartbeatsLastHour: number
  jobsLastHour: number
  jobsFailedLastHour: number
  errorsLastHour: number
}

/** Execução de um job @Scheduled. */
export type JobRun = {
  id: string
  jobName: string
  startedAt: string
  finishedAt: string | null
  status: 'running' | 'success' | 'failed'
  errorMessage: string | null
}

/** Erro registrado em ponto cravado (OutboundService / GeminiProvider). */
export type ErrorEntry = {
  id: string
  source: string
  message: string
  stackTrace: string | null
  context: unknown
  createdAt: string
}

export function getHealth(): Promise<HealthSummary> {
  return apiFetch<HealthSummary>('/admin/health')
}

export function getJobs(): Promise<{ items: JobRun[] }> {
  return apiFetch<{ items: JobRun[] }>('/admin/jobs')
}

export function getErrors(source?: string): Promise<{ items: ErrorEntry[] }> {
  const q = source ? `?source=${encodeURIComponent(source)}` : ''
  return apiFetch<{ items: ErrorEntry[] }>(`/admin/errors${q}`)
}
