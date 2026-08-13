'use client'

import { useQuery } from '@tanstack/react-query'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { PageSkeleton } from '@/components/ui/skeleton'
import { getHealth, type HealthSummary } from '@/lib/api/admin/health'

/** Card de contador 1h (rótulo + número; vermelho quando danger e > 0). */
function CounterCard({ label, value, danger }: { label: string; value: number; danger?: boolean }) {
  return (
    <Card>
      <div
        className={`text-3xl font-semibold tabular-nums ${danger && value > 0 ? 'text-red-600' : ''}`}
      >
        {value.toLocaleString('pt-BR')}
      </div>
      <div className="mt-1 text-sm text-muted-foreground">{label}</div>
      <div className="text-xs text-muted-foreground">última hora</div>
    </Card>
  )
}

/**
 * Saúde da plataforma (camada 6.4, super-admin). Status do webhook (on/off via dry-run),
 * último heartbeat e contadores da última hora (heartbeats, jobs, jobs falhos, erros).
 * Webhook está OFF no MVP → "sem heartbeat" é o estado esperado.
 */
export default function HealthPage() {
  const { data, isPending, isError } = useQuery<HealthSummary>({
    queryKey: ['admin-health'],
    queryFn: getHealth,
  })

  if (isPending) return <PageSkeleton />

  if (isError || !data) {
    return (
      <div className="space-y-6">
        <PageHeader title="Saúde da plataforma" description="Status do webhook, jobs e erros." />
        <p className="text-sm text-destructive">Erro ao carregar a saúde da plataforma.</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Saúde da plataforma"
        description="Status do webhook, jobs e erros recentes."
      />

      <Card className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium">Webhook da Evolution</span>
          {data.webhookOff ? (
            <Badge variant="muted">Desligado (dry-run)</Badge>
          ) : (
            <Badge variant="success">Ativo</Badge>
          )}
        </div>
        <div className="text-sm text-muted-foreground">
          Último heartbeat:{' '}
          {data.lastHeartbeatAt ? (
            new Date(data.lastHeartbeatAt).toLocaleString('pt-BR')
          ) : (
            <span className="italic">sem heartbeat</span>
          )}
        </div>
      </Card>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <CounterCard label="Heartbeats" value={data.heartbeatsLastHour} />
        <CounterCard label="Jobs executados" value={data.jobsLastHour} />
        <CounterCard label="Jobs com falha" value={data.jobsFailedLastHour} danger />
        <CounterCard label="Erros" value={data.errorsLastHour} danger />
      </div>
    </div>
  )
}
