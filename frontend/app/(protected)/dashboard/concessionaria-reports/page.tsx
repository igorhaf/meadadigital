'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Card, Section } from '@/components/ui/card'
import { getReportSummary } from '@/lib/api/concessionaria/reports'
import { LEAD_STATUSES } from '@/profiles/concessionaria/concessionaria-lead-status'
import { TEST_DRIVE_STATUSES } from '@/profiles/concessionaria/concessionaria-test-drive-status'
import { formatBrl } from '@/profiles/concessionaria/concessionaria-types'

const WINDOWS = [3, 6, 12, 24]

function monthLabel(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-')
  return `${m}/${y}`
}

function statusLabelOf(list: readonly { id: string; label: string }[], id?: string): string {
  return list.find((s) => s.id === id)?.label ?? id ?? '—'
}

/**
 * Dashboard comercial do ConcessionariaBot (onda 1, backlog #10): funil de leads, conversão na
 * janela, desempenho por vendedor (leads fechados + test-drives realizados) e vendas por mês
 * (veículos marcados 'vendido').
 */
export default function ConcessionariaReportsPage() {
  const [months, setMonths] = useState(6)

  const { data, isPending, isError } = useQuery({
    queryKey: ['concessionaria-reports', months],
    queryFn: () => getReportSummary(months),
  })

  const conversion =
    data && data.leadsCreated > 0 ? Math.round((data.leadsClosed / data.leadsCreated) * 100) : 0

  return (
    <div className="space-y-6">
      <PageHeader
        title="Relatórios"
        description="Funil de leads, conversão, desempenho por vendedor e vendas por mês."
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
          <div className="grid grid-cols-3 gap-4">
            <Card>
              <p className="text-xs text-muted-foreground">Leads criados no período</p>
              <p className="text-2xl font-semibold tabular-nums">{data.leadsCreated}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Leads fechados</p>
              <p className="text-2xl font-semibold tabular-nums">{data.leadsClosed}</p>
            </Card>
            <Card>
              <p className="text-xs text-muted-foreground">Conversão</p>
              <p className="text-2xl font-semibold tabular-nums">{conversion}%</p>
            </Card>
          </div>

          <Card>
            <Section title="Funil de leads (snapshot por status)">
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs text-muted-foreground">
                      <th className="px-3 py-2 font-medium">Status</th>
                      <th className="px-3 py-2 text-right font-medium">Leads</th>
                      <th className="px-3 py-2 text-right font-medium">Valor (catálogo)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {data.funnel.map((r, i) => (
                      <tr key={i}>
                        <td className="px-3 py-2">{statusLabelOf(LEAD_STATUSES, r.status)}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{r.count}</td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {formatBrl(r.totalCents ?? 0)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>
          </Card>

          <Card>
            <Section title="Por vendedor (no período)">
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs text-muted-foreground">
                      <th className="px-3 py-2 font-medium">Vendedor</th>
                      <th className="px-3 py-2 text-right font-medium">Test-drives realizados</th>
                      <th className="px-3 py-2 text-right font-medium">Leads fechados</th>
                      <th className="px-3 py-2 text-right font-medium">Valor fechado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {data.bySalesperson.map((r, i) => (
                      <tr key={i}>
                        <td className="px-3 py-2">{r.salesperson ?? '—'}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{r.testDrives ?? 0}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{r.closedLeads ?? 0}</td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {formatBrl(r.closedCents ?? 0)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>
          </Card>

          <Card>
            <Section title="Vendas por mês (veículos vendidos, valor de catálogo)">
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs text-muted-foreground">
                      <th className="px-3 py-2 font-medium">Mês</th>
                      <th className="px-3 py-2 text-right font-medium">Vendas</th>
                      <th className="px-3 py-2 text-right font-medium">Valor</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {data.salesByMonth.map((r, i) => (
                      <tr key={i}>
                        <td className="px-3 py-2">{r.month ? monthLabel(r.month) : '—'}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{r.count}</td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {formatBrl(r.totalCents ?? 0)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>
          </Card>

          <Card>
            <Section title="Test-drives por status (agendados no período)">
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs text-muted-foreground">
                      <th className="px-3 py-2 font-medium">Status</th>
                      <th className="px-3 py-2 text-right font-medium">Test-drives</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {data.testDrivesByStatus.map((r, i) => (
                      <tr key={i}>
                        <td className="px-3 py-2">
                          {statusLabelOf(TEST_DRIVE_STATUSES, r.status)}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">{r.count}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>
          </Card>
        </>
      )}
    </div>
  )
}
