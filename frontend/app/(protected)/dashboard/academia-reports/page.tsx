'use client'

import { useQuery } from '@tanstack/react-query'

import { PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'
import { getOccupancyReport, getSummaryReport } from '@/lib/api/academia/reports'
import { dayOfWeekLabel, formatPrice } from '@/profiles/academia/academia-types'

/**
 * Relatórios gerenciais do AcademiaBot (somente leitura): MRR + contagem de matrículas por
 * status e ocupação (vagas) por aula. Chaves em snake_case direto do backend.
 */
export default function AcademiaReportsPage() {
  const summary = useQuery({
    queryKey: ['academia-reports-summary'],
    queryFn: getSummaryReport,
  })

  const occupancy = useQuery({
    queryKey: ['academia-reports-occupancy'],
    queryFn: getOccupancyReport,
  })

  const rows = occupancy.data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Relatórios"
        description="Visão gerencial da academia: receita recorrente, matrículas e ocupação das aulas."
      />

      {summary.isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o resumo.</p>
      ) : summary.isPending ? (
        <p className="text-sm text-muted-foreground">Carregando resumo…</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Card>
            <p className="text-sm text-muted-foreground">MRR</p>
            <p className="mt-1 text-3xl font-semibold tabular-nums">
              {formatPrice(summary.data.mrr_cents)}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">Receita mensal recorrente</p>
          </Card>
          <Card>
            <p className="text-sm text-muted-foreground">Matrículas ativas</p>
            <p className="mt-1 text-3xl font-semibold tabular-nums">{summary.data.active_count}</p>
            <p className="mt-1 text-xs text-muted-foreground">pagando todo mês</p>
          </Card>
          <Card>
            <p className="text-sm text-muted-foreground">Suspensas</p>
            <p className="mt-1 text-3xl font-semibold tabular-nums">
              {summary.data.suspended_count}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">pausadas, mantêm a vaga</p>
          </Card>
          <Card>
            <p className="text-sm text-muted-foreground">Canceladas</p>
            <p className="mt-1 text-3xl font-semibold tabular-nums">
              {summary.data.canceled_count}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">encerradas</p>
          </Card>
        </div>
      )}

      <section className="space-y-3">
        <h2 className="text-base font-semibold">Ocupação por aula</h2>
        {occupancy.isError ? (
          <p className="text-sm text-destructive">Erro ao carregar a ocupação.</p>
        ) : occupancy.isPending ? (
          <p className="text-sm text-muted-foreground">Carregando ocupação…</p>
        ) : rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhuma aula cadastrada ainda.</p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="px-3 py-2 font-medium">Aula</th>
                  <th className="px-3 py-2 font-medium">Dia</th>
                  <th className="px-3 py-2 font-medium">Ocupação</th>
                  <th className="w-1/3 px-3 py-2 font-medium">Vagas preenchidas</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const pct = r.capacity > 0 ? Math.round((r.active_count / r.capacity) * 100) : 0
                  const full = pct >= 100
                  return (
                    <tr key={r.class_id} className="border-b border-border last:border-b-0">
                      <td className="px-3 py-2 font-medium">{r.class_name}</td>
                      <td className="px-3 py-2">{dayOfWeekLabel(r.day_of_week)}</td>
                      <td className="px-3 py-2 tabular-nums">
                        {r.active_count}/{r.capacity}
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2">
                          <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                            <div
                              className={`h-full rounded-full ${full ? 'bg-destructive' : 'bg-foreground/60'}`}
                              style={{ width: `${Math.min(pct, 100)}%` }}
                            />
                          </div>
                          <span
                            className={`w-10 text-right text-xs tabular-nums ${full ? 'font-semibold text-destructive' : 'text-muted-foreground'}`}
                          >
                            {pct}%
                          </span>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
