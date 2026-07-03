'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Card, Section } from '@/components/ui/card'
import { getReportSummary } from '@/lib/api/barbearia/reports'
import { formatPrice, type BarberReportRow } from '@/profiles/barbearia/barber-types'

const WINDOWS = [3, 6, 12, 24]

function monthLabel(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-')
  return `${m}/${y}`
}

function RowsTable({ rows, firstColumn, firstValue, withNoShows }: {
  rows: BarberReportRow[]
  firstColumn: string
  firstValue: (r: BarberReportRow) => string
  withNoShows?: boolean
}) {
  if (rows.length === 0) {
    return <p className="text-xs text-muted-foreground">Nenhum atendimento realizado no período.</p>
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs text-muted-foreground">
            <th className="px-3 py-2 font-medium">{firstColumn}</th>
            <th className="px-3 py-2 text-right font-medium">Realizados</th>
            {withNoShows && <th className="px-3 py-2 text-right font-medium">Faltas</th>}
            <th className="px-3 py-2 text-right font-medium">Faturamento</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((r, i) => (
            <tr key={i}>
              <td className="px-3 py-2">{firstValue(r)}</td>
              <td className="px-3 py-2 text-right tabular-nums">{r.count}</td>
              {withNoShows && <td className="px-3 py-2 text-right tabular-nums">{r.noShows ?? 0}</td>}
              <td className="px-3 py-2 text-right tabular-nums">{formatPrice(r.totalCents)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Relatórios do BarbeariaBot (onda 1, backlog #15): faturamento LÍQUIDO dos realizados (corte grátis
 * da fidelidade fatura 0), taxa de falta, ocupação por barbeiro e ranking de serviços.
 */
export default function BarberReportsPage() {
  const [months, setMonths] = useState(6)

  const { data, isPending, isError } = useQuery({
    queryKey: ['barber-reports', months],
    queryFn: () => getReportSummary(months),
  })

  const noShowRate = data && data.realizedCount + data.noShowCount > 0
    ? Math.round((data.noShowCount / (data.realizedCount + data.noShowCount)) * 100)
    : 0

  return (
    <div className="space-y-6">
      <PageHeader
        title="Relatórios"
        description="Faturamento dos atendimentos realizados (líquido, com desconto/fidelidade abatidos) e taxa de falta."
      />

      <div className="flex flex-wrap items-center gap-2">
        {WINDOWS.map((w) => (
          <button key={w} onClick={() => setMonths(w)}
            className={`rounded-full border px-3 py-1 text-xs ${months === w ? 'border-primary bg-primary/10' : 'border-border'}`}>
            {w} meses
          </button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o relatório.</p>
      ) : isPending || !data ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Card>
              <p className="text-xs text-muted-foreground">Realizados</p>
              <p className="text-2xl font-semibold tabular-nums">{data.realizedCount}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Faturamento</p>
              <p className="text-2xl font-semibold tabular-nums">{formatPrice(data.totalCents)}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Taxa de falta</p>
              <p className="text-2xl font-semibold tabular-nums">{noShowRate}%</p>
              <p className="text-xs text-muted-foreground">{data.noShowCount} falta(s)</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Cancelados</p>
              <p className="text-2xl font-semibold tabular-nums">{data.cancelledCount}</p>
            </Card>
          </div>

          <Card>
            <Section title="Por mês">
              <RowsTable rows={data.byMonth} firstColumn="Mês"
                firstValue={(r) => (r.month ? monthLabel(r.month) : '—')} />
            </Section>
          </Card>

          <Card>
            <Section title="Por barbeiro">
              <RowsTable rows={data.byBarber} firstColumn="Barbeiro" withNoShows
                firstValue={(r) => r.barberName ?? '—'} />
            </Section>
          </Card>

          <Card>
            <Section title="Ranking de serviços">
              <RowsTable rows={data.byService} firstColumn="Serviço"
                firstValue={(r) => r.serviceName ?? '—'} />
            </Section>
          </Card>
        </>
      )}
    </div>
  )
}
