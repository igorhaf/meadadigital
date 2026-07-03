'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Card, Section } from '@/components/ui/card'
import { getReportSummary } from '@/lib/api/casamento/reports'
import { formatBrl, type WeddingReportRow } from '@/profiles/casamento/casamento-types'
import {
  statusLabel,
  type WeddingProposalStatusId,
} from '@/profiles/casamento/wedding-proposal-status'

const WINDOWS = [6, 12, 24]

function monthLabel(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-')
  return `${m}/${y}`
}

function RowsTable({
  rows,
  firstColumn,
  firstValue,
  countColumn = 'Casamentos',
}: {
  rows: WeddingReportRow[]
  firstColumn: string
  firstValue: (r: WeddingReportRow) => string
  countColumn?: string
}) {
  if (rows.length === 0) {
    return <p className="text-xs text-muted-foreground">Nada no período.</p>
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs text-muted-foreground">
            <th className="px-3 py-2 font-medium">{firstColumn}</th>
            <th className="px-3 py-2 text-right font-medium">{countColumn}</th>
            <th className="px-3 py-2 text-right font-medium">Valor</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((r, i) => (
            <tr key={i}>
              <td className="px-3 py-2">{firstValue(r)}</td>
              <td className="px-3 py-2 text-right tabular-nums">{r.count}</td>
              <td className="px-3 py-2 text-right tabular-nums">{formatBrl(r.totalCents)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Dashboard comercial do CasamentoBot (onda 1, backlog #14): receita REALIZADA (casamentos
 * entregues, valor líquido) por mês/assessor + receita PREVISTA (contratos fechados por mês do
 * casamento) + funil por status.
 */
export default function CasamentoReportsPage() {
  const [months, setMonths] = useState(12)

  const { data, isPending, isError } = useQuery({
    queryKey: ['casamento-reports', months],
    queryFn: () => getReportSummary(months),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Relatórios"
        description="Receita realizada (líquida), receita prevista dos contratos fechados e funil comercial."
      />

      <div className="flex flex-wrap items-center gap-2">
        {WINDOWS.map((w) => (
          <button
            key={w}
            onClick={() => setMonths(w)}
            className={`rounded-full border px-3 py-1 text-xs ${months === w ? 'border-primary bg-primary/10' : 'border-border'}`}
          >
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
          <div className="grid grid-cols-2 gap-4">
            <Card>
              <p className="text-xs text-muted-foreground">Casamentos realizados no período</p>
              <p className="text-2xl font-semibold tabular-nums">{data.totalCount}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Receita realizada no período</p>
              <p className="text-2xl font-semibold tabular-nums">{formatBrl(data.totalCents)}</p>
            </Card>
          </div>

          <Card>
            <Section title="Receita realizada por mês">
              <RowsTable
                rows={data.byMonth}
                firstColumn="Mês"
                firstValue={(r) => (r.month ? monthLabel(r.month) : '—')}
              />
            </Section>
          </Card>

          <Card>
            <Section title="Receita prevista (contratos fechados, por mês do casamento)">
              <RowsTable
                rows={data.upcomingByMonth}
                firstColumn="Mês do casamento"
                firstValue={(r) => (r.month ? monthLabel(r.month) : '—')}
              />
            </Section>
          </Card>

          <Card>
            <Section title="Por assessor (realizadas)">
              <RowsTable
                rows={data.byPlanner}
                firstColumn="Assessor"
                firstValue={(r) => r.plannerName ?? 'Sem atribuição'}
              />
            </Section>
          </Card>

          <Card>
            <Section title="Funil (propostas por status)">
              <RowsTable
                rows={data.funnel}
                firstColumn="Status"
                countColumn="Propostas"
                firstValue={(r) =>
                  r.status ? statusLabel(r.status as WeddingProposalStatusId) : '—'
                }
              />
            </Section>
          </Card>
        </>
      )}
    </div>
  )
}
