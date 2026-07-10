'use client'

import { useQuery } from '@tanstack/react-query'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { DataTable, type Column } from '@/components/ui/data-table'
import { getJobs, type JobRun } from '@/lib/api/admin/health'

/** Badge de status da execução (running/success/failed). */
function StatusBadge({ status }: { status: JobRun['status'] }) {
  if (status === 'success') return <Badge variant="success">Sucesso</Badge>
  if (status === 'failed') return <Badge variant="danger">Falhou</Badge>
  return <Badge variant="muted">Em execução</Badge>
}

const columns: Column<JobRun>[] = [
  {
    key: 'jobName',
    header: 'Job',
    render: (r) => <span className="font-medium">{r.jobName}</span>,
  },
  { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
  {
    key: 'startedAt',
    header: 'Início',
    render: (r) => new Date(r.startedAt).toLocaleString('pt-BR'),
  },
  {
    key: 'finishedAt',
    header: 'Fim',
    render: (r) => (r.finishedAt ? new Date(r.finishedAt).toLocaleString('pt-BR') : '—'),
  },
  {
    key: 'errorMessage',
    header: 'Erro',
    render: (r) => (
      <span className="line-clamp-1 text-xs text-muted-foreground">{r.errorMessage ?? '—'}</span>
    ),
  },
]

/**
 * Jobs agendados (camada 6.4, super-admin). Últimas 20 execuções por job (ReminderJob,
 * ReactivationJob): início, fim, status e mensagem de erro. READ-only.
 */
export default function JobsPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-jobs'],
    queryFn: getJobs,
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Jobs agendados"
        description="Execuções recentes dos jobs cron (lembretes, reativação)."
      />
      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os jobs.</p>
      ) : (
        <DataTable<JobRun>
          data={data?.items ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhuma execução de job registrada ainda."
        />
      )}
    </div>
  )
}
