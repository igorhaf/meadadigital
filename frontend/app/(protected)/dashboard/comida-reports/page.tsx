'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Card, Section } from '@/components/ui/card'
import { getReportSummary } from '@/lib/api/comida/reports'
import { formatBrl, type ComidaReportRow } from '@/profiles/comida/comida-types'

const WINDOWS = [1, 3, 6, 12]

function monthLabel(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-')
  return `${m}/${y}`
}

function RowsTable({
  rows,
  firstColumn,
  firstValue,
  showRevenue = true,
}: {
  rows: ComidaReportRow[]
  firstColumn: string
  firstValue: (r: ComidaReportRow) => string
  showRevenue?: boolean
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
            <th className="px-3 py-2 text-right font-medium">Pedidos</th>
            {showRevenue && <th className="px-3 py-2 text-right font-medium">Faturamento</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((r, i) => (
            <tr key={i}>
              <td className="px-3 py-2">{firstValue(r)}</td>
              <td className="px-3 py-2 text-right tabular-nums">{r.count}</td>
              {showRevenue && (
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatBrl(r.totalCents ?? 0)}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Relatório de vendas do ComidaBot (onda 1, backlog #15): faturamento dos pedidos ENTREGUES (valor
 * líquido, desconto já abatido), ticket médio, top itens e horário de pico (demanda por hora).
 */
export default function ComidaReportsPage() {
  const [months, setMonths] = useState(3)

  const { data, isPending, isError } = useQuery({
    queryKey: ['comida-reports', months],
    queryFn: () => getReportSummary(months),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Relatórios"
        description="Vendas entregues, ticket médio, itens campeões e horário de pico — decisão com dado, não achismo."
      />

      <div className="flex flex-wrap items-center gap-2">
        {WINDOWS.map((w) => (
          <button
            key={w}
            onClick={() => setMonths(w)}
            className={`rounded-full border px-3 py-1 text-xs ${months === w ? 'border-primary bg-primary/10' : 'border-border'}`}
          >
            {w === 1 ? '1 mês' : `${w} meses`}
          </button>
        ))}
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar o relatório.</p>
      ) : isPending || !data ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : (
        <>
          <div className="grid grid-cols-3 gap-4">
            <Card>
              <p className="text-xs text-muted-foreground">Pedidos entregues</p>
              <p className="text-2xl font-semibold tabular-nums">{data.totalCount}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Faturamento</p>
              <p className="text-2xl font-semibold tabular-nums">{formatBrl(data.totalCents)}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Ticket médio</p>
              <p className="text-2xl font-semibold tabular-nums">
                {formatBrl(data.avgTicketCents)}
              </p>
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
            <Section title="Top itens (entregues)">
              <RowsTable
                rows={data.topItems}
                firstColumn="Item"
                firstValue={(r) => r.item ?? '—'}
              />
            </Section>
          </Card>

          <Card>
            <Section title="Horário de pico (pedidos por hora, todos os status)">
              <RowsTable
                rows={data.byHour}
                firstColumn="Hora"
                showRevenue={false}
                firstValue={(r) => `${String(r.hour).padStart(2, '0')}h`}
              />
            </Section>
          </Card>
        </>
      )}
    </div>
  )
}
