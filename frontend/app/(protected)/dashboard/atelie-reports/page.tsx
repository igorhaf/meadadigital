'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Card, Section } from '@/components/ui/card'
import { getReportSummary } from '@/lib/api/atelie/reports'
import { typeLabel, type AtelieProjectTypeId } from '@/profiles/atelie/atelie-project-type'
import { formatBrl, type AtelieReportRow } from '@/profiles/atelie/atelie-types'

const WINDOWS = [3, 6, 12, 24]

function monthLabel(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-')
  return `${m}/${y}`
}

function RowsTable({
  rows,
  firstColumn,
  firstValue,
}: {
  rows: AtelieReportRow[]
  firstColumn: string
  firstValue: (r: AtelieReportRow) => string
}) {
  if (rows.length === 0) {
    return <p className="text-xs text-muted-foreground">Nenhuma peça entregue no período.</p>
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs text-muted-foreground">
            <th className="px-3 py-2 font-medium">{firstColumn}</th>
            <th className="px-3 py-2 text-right font-medium">Peças entregues</th>
            <th className="px-3 py-2 text-right font-medium">Faturamento</th>
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
 * Relatório de faturamento do AtelieBot (onda 2, backlog #14): propostas REALIZADAS (peça entregue),
 * valor LÍQUIDO (total − desconto), por mês / tipo de projeto / artesão. Janela em meses.
 */
export default function AtelieReportsPage() {
  const [months, setMonths] = useState(6)

  const { data, isPending, isError } = useQuery({
    queryKey: ['atelie-reports', months],
    queryFn: () => getReportSummary(months),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Relatórios"
        description="Faturamento das peças entregues (valor líquido, com desconto de cupom já abatido)."
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
              <p className="text-xs text-muted-foreground">Peças entregues no período</p>
              <p className="text-2xl font-semibold tabular-nums">{data.totalCount}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Faturamento no período</p>
              <p className="text-2xl font-semibold tabular-nums">{formatBrl(data.totalCents)}</p>
            </Card>
          </div>

          <Card>
            <Section title="Por mês">
              <RowsTable
                rows={data.byMonth}
                firstColumn="Mês"
                firstValue={(r) => (r.month ? monthLabel(r.month) : '—')}
              />
            </Section>
          </Card>

          <Card>
            <Section title="Por tipo de projeto">
              <RowsTable
                rows={data.byType}
                firstColumn="Tipo"
                firstValue={(r) =>
                  r.projectType ? typeLabel(r.projectType as AtelieProjectTypeId) : '—'
                }
              />
            </Section>
          </Card>

          <Card>
            <Section title="Por artesão">
              <RowsTable
                rows={data.byArtisan}
                firstColumn="Artesão"
                firstValue={(r) => r.artisanName ?? 'Sem atribuição'}
              />
            </Section>
          </Card>
        </>
      )}
    </div>
  )
}
