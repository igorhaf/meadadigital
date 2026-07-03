'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { DataTable, type Column } from '@/components/ui/data-table'
import { PageSkeleton } from '@/components/ui/skeleton'
import {
  getGlobalMetrics,
  type AtRiskTenant,
  type GlobalMetrics,
  type TopTenant,
} from '@/lib/api/admin/metrics'
import { getMe } from '@/lib/api/me'
import {
  downloadMetricsPdf,
  getMetricsComparison,
  type MetricsComparison,
  type MonthlyCounts,
} from '@/lib/api/metrics-extra'
import { getTenantMetrics, type MessagesByDay, type TenantMetrics } from '@/lib/supabase/metrics'

/** Card de número grande com rótulo. */
function MetricCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <div className="text-3xl font-semibold tabular-nums">{value.toLocaleString('pt-BR')}</div>
      <div className="mt-1 text-sm text-muted-foreground">{label}</div>
      <div className="text-xs text-muted-foreground">últimos 30 dias</div>
    </Card>
  )
}

/** Formata segundos em "12s" / "1m 23s" / "—". */
function formatSeconds(s: number | null): string {
  if (s == null) return '—'
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const rest = s % 60
  return rest === 0 ? `${m}m` : `${m}m ${rest}s`
}

/**
 * Gráfico de linhas SVG manual (sem dependência): mensagens/dia, 30 dias, 2 séries
 * (recebidas/enviadas). viewBox 600x200; escala Y pelo máximo das duas séries; grid
 * horizontal discreto; rótulos do primeiro/meio/último dia no eixo X.
 */
function MessagesChart({ data }: { data: MessagesByDay[] }) {
  const W = 600
  const H = 200
  const PAD = 24
  const n = data.length
  const maxY = Math.max(1, ...data.map((d) => Math.max(d.inbound, d.outbound)))

  const x = (i: number) => PAD + (i * (W - 2 * PAD)) / Math.max(1, n - 1)
  const y = (v: number) => H - PAD - (v * (H - 2 * PAD)) / maxY

  const line = (key: 'inbound' | 'outbound') =>
    data
      .map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(d[key]).toFixed(1)}`)
      .join(' ')

  // 3 linhas de grade horizontais (0, meio, topo).
  const gridYs = [0, maxY / 2, maxY]

  return (
    <Card>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-medium">Mensagens por dia</h2>
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="inline-block size-2 rounded-full bg-[#2b5c8a]" /> Recebidas
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block size-2 rounded-full bg-[#3a6b4a]" /> Enviadas
          </span>
        </div>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} className="h-48 w-full" preserveAspectRatio="none">
        {gridYs.map((gv, i) => (
          <line
            key={i}
            x1={PAD}
            x2={W - PAD}
            y1={y(gv)}
            y2={y(gv)}
            className="stroke-border"
            strokeWidth={1}
          />
        ))}
        <path d={line('inbound')} fill="none" stroke="#2b5c8a" strokeWidth={2} />
        <path d={line('outbound')} fill="none" stroke="#3a6b4a" strokeWidth={2} />
      </svg>
      <div className="mt-1 flex justify-between text-xs text-muted-foreground">
        <span>{data[0]?.day.slice(5)}</span>
        <span>{data[Math.floor(n / 2)]?.day.slice(5)}</span>
        <span>{data[n - 1]?.day.slice(5)}</span>
      </div>
    </Card>
  )
}

/** Rótulos pt-BR das 4 métricas comparadas (ordem fixa de exibição). */
const COMPARISON_ROWS: { key: keyof MonthlyCounts; label: string }[] = [
  { key: 'conversations', label: 'Conversas iniciadas' },
  { key: 'messagesInbound', label: 'Mensagens recebidas' },
  { key: 'messagesOutbound', label: 'Mensagens enviadas' },
  { key: 'activeContacts', label: 'Contatos ativos' },
]

/** Indicador de variação: verde ↑ (subiu), vermelho ↓ (caiu), neutro — (igual). */
function DeltaBadge({ delta }: { delta: number }) {
  if (delta > 0) {
    return (
      <span className="text-sm font-medium text-green-600 tabular-nums">
        ↑ +{delta.toLocaleString('pt-BR')}
      </span>
    )
  }
  if (delta < 0) {
    return (
      <span className="text-sm font-medium text-red-600 tabular-nums">
        ↓ {delta.toLocaleString('pt-BR')}
      </span>
    )
  }
  return <span className="text-sm text-muted-foreground tabular-nums">— 0</span>
}

/**
 * Seção "Comparação mês a mês" (#66): por métrica, mostra o valor do mês atual e a variação
 * vs o mês anterior. Dados do backend (getMetricsComparison). Some em silêncio se a comparação
 * não carregar (a tela principal de métricas continua útil).
 */
function ComparisonSection({ data }: { data: MetricsComparison }) {
  return (
    <Card>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {COMPARISON_ROWS.map((r) => (
          <div
            key={r.key}
            className="flex items-baseline justify-between rounded-lg border border-border bg-background p-3"
          >
            <div>
              <div className="text-2xl font-semibold tabular-nums">
                {data.current[r.key].toLocaleString('pt-BR')}
              </div>
              <div className="text-xs text-muted-foreground">{r.label}</div>
            </div>
            <div className="text-right">
              <DeltaBadge delta={data.deltas[r.key]} />
              <div className="text-xs text-muted-foreground">
                mês anterior: {data.previous[r.key].toLocaleString('pt-BR')}
              </div>
            </div>
          </div>
        ))}
      </div>
      <p className="mt-3 text-xs text-muted-foreground">
        Mês calendário atual comparado ao anterior (variação = atual − anterior).
      </p>
    </Card>
  )
}

export default function MetricsPage() {
  const [exporting, setExporting] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'
  const isSuperAdmin = me?.role === 'super_admin'

  const { data, isPending, isError, error } = useQuery<TenantMetrics>({
    queryKey: ['tenant-metrics'],
    queryFn: getTenantMetrics,
    enabled: isTenant,
  })

  // Comparação mês a mês (#66) — query separada (backend). Falha silenciosa: a tela
  // principal de métricas continua útil sem ela.
  const { data: comparison } = useQuery<MetricsComparison>({
    queryKey: ['metrics-comparison'],
    queryFn: getMetricsComparison,
    enabled: isTenant,
  })

  async function handleExportPdf() {
    setExporting(true)
    try {
      await downloadMetricsPdf()
    } catch (e) {
      console.error('failed to export metrics pdf:', e)
    } finally {
      setExporting(false)
    }
  }

  // Super-admin: métricas GLOBAIS reais de toda a plataforma (camada 6.3). Tenant segue
  // com a tela atual (volume/desempenho da própria empresa).
  if (isSuperAdmin) {
    return <GlobalMetricsView />
  }

  if (isError) {
    console.error('failed to load metrics:', error)
    return (
      <div className="space-y-6">
        <PageHeader title="Métricas" />
        <p className="text-sm text-destructive">Erro ao carregar métricas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isPending) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Métricas"
        description="Volume de mensagens, conversas e desempenho da IA nos últimos 30 dias."
        actions={
          <Button variant="outline" onClick={handleExportPdf} disabled={exporting}>
            {exporting ? 'Exportando…' : 'Exportar PDF'}
          </Button>
        }
      />

      {data && (
        <>
          {/* Cards principais — números dos últimos 30 dias */}
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <MetricCard label="Mensagens recebidas" value={data.messagesInbound30d} />
            <MetricCard label="Mensagens enviadas" value={data.messagesOutbound30d} />
            <MetricCard label="Conversas iniciadas" value={data.conversationsStarted30d} />
            <MetricCard label="Contatos novos" value={data.contactsNew30d} />
          </div>

          {/* Gráfico de mensagens por dia */}
          <MessagesChart data={data.messagesByDay} />

          {comparison && (
            <Section title="Comparação mês a mês">
              <ComparisonSection data={comparison} />
            </Section>
          )}

          <Section title="Desempenho e FAQs">
            <div className="grid gap-6 md:grid-cols-2">
              <Card>
                <h2 className="mb-1 text-sm font-medium">Tempo médio de resposta da IA</h2>
                <div className="text-3xl font-semibold tabular-nums">
                  {formatSeconds(data.avgResponseSeconds)}
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  média entre a mensagem do cliente e a resposta da IA (últimos 30 dias)
                </p>
              </Card>

              <Card>
                <h2 className="mb-2 text-sm font-medium">FAQs ativas</h2>
                {data.topFaqs.length === 0 ? (
                  <p className="text-sm text-muted-foreground">Nenhuma FAQ ativa.</p>
                ) : (
                  <ol className="space-y-1 text-sm">
                    {data.topFaqs.map((f, i) => (
                      <li key={f.id} className="flex gap-2">
                        <span className="text-muted-foreground">{i + 1}.</span>
                        <span className="truncate">{f.question}</span>
                      </li>
                    ))}
                  </ol>
                )}
                <p className="mt-3 text-xs text-muted-foreground">
                  Ordem cronológica. O ranking por uso real fica para uma fase futura, quando houver
                  rastreamento de qual FAQ a IA usou em cada resposta.
                </p>
              </Card>
            </div>
          </Section>
        </>
      )}
    </div>
  )
}

/** Card de KPI grande para a visão global do super-admin (rótulo + número + sufixo opcional). */
function KpiCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <Card>
      <div className="text-3xl font-semibold tabular-nums">{value.toLocaleString('pt-BR')}</div>
      <div className="mt-1 text-sm text-muted-foreground">{label}</div>
      {hint && <div className="text-xs text-muted-foreground">{hint}</div>}
    </Card>
  )
}

/** Variação percentual (verde ↑ / vermelho ↓ / neutro —). */
function PctDelta({ pct }: { pct: number }) {
  if (pct > 0)
    return <span className="text-sm font-medium text-green-600 tabular-nums">↑ +{pct}%</span>
  if (pct < 0)
    return <span className="text-sm font-medium text-red-600 tabular-nums">↓ {pct}%</span>
  return <span className="text-sm text-muted-foreground tabular-nums">— 0%</span>
}

/**
 * Métricas GLOBAIS da plataforma (camada 6.3, super-admin). KPIs grandes, comparação mês a mês,
 * top empresas por volume, empresas em risco e crescimento (6 meses). Tokens são números reais
 * (somados das mensagens — 6.2.5); serão 0 enquanto nenhum fluxo de IA tiver rodado.
 */
function GlobalMetricsView() {
  const { data, isPending, isError } = useQuery<GlobalMetrics>({
    queryKey: ['global-metrics'],
    queryFn: getGlobalMetrics,
  })

  if (isPending) return <PageSkeleton />

  if (isError || !data) {
    return (
      <div className="space-y-6">
        <PageHeader title="Métricas" description="Métricas globais da plataforma." />
        <p className="text-sm text-destructive">Erro ao carregar métricas globais.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  const { kpis, comparison, topTenants, atRisk, companiesCreatedPerMonth } = data

  const topColumns: Column<TopTenant>[] = [
    {
      key: 'name',
      header: 'Empresa',
      render: (t) => (
        <Link href={`/dashboard/companies/${t.id}`} className="hover:underline">
          {t.name}
        </Link>
      ),
    },
    {
      key: 'messagesLast30d',
      header: 'Mensagens (30d)',
      render: (t) => (
        <span className="tabular-nums">{t.messagesLast30d.toLocaleString('pt-BR')}</span>
      ),
    },
  ]

  const riskColumns: Column<AtRiskTenant>[] = [
    {
      key: 'name',
      header: 'Empresa',
      render: (t) => (
        <Link href={`/dashboard/companies/${t.id}`} className="hover:underline">
          {t.name}
        </Link>
      ),
    },
    {
      key: 'lastActivityAt',
      header: 'Última atividade',
      render: (t) =>
        t.lastActivityAt ? (
          new Date(t.lastActivityAt).toLocaleDateString('pt-BR')
        ) : (
          <span className="text-muted-foreground">nunca</span>
        ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader title="Métricas" description="Visão global de toda a plataforma." />

      {/* KPIs grandes */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
        <KpiCard label="Empresas (total)" value={kpis.totalCompanies} />
        <KpiCard label="Empresas ativas" value={kpis.activeCompanies} />
        <KpiCard label="Mensagens" value={kpis.totalMessages30d} hint="últimos 30 dias" />
        <KpiCard label="Conversas" value={kpis.totalConversations30d} hint="últimos 30 dias" />
        <KpiCard label="Tokens Gemini" value={kpis.geminiTokensThisMonth} hint="mês atual" />
        <KpiCard label="Tokens Gemini" value={kpis.geminiTokensLast30d} hint="últimos 30 dias" />
      </div>

      {/* Comparação mês a mês */}
      <Section title="Comparação mês a mês">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="flex items-baseline justify-between rounded-lg border border-border bg-background p-3">
            <div>
              <div className="text-2xl font-semibold tabular-nums">
                {comparison.messagesThisMonth.toLocaleString('pt-BR')}
              </div>
              <div className="text-xs text-muted-foreground">Mensagens (mês atual)</div>
            </div>
            <div className="text-right">
              <PctDelta pct={comparison.messagesDeltaPct} />
              <div className="text-xs text-muted-foreground">
                anterior: {comparison.messagesLastMonth.toLocaleString('pt-BR')}
              </div>
            </div>
          </div>
          <div className="flex items-baseline justify-between rounded-lg border border-border bg-background p-3">
            <div>
              <div className="text-2xl font-semibold tabular-nums">
                {comparison.companiesThisMonth.toLocaleString('pt-BR')}
              </div>
              <div className="text-xs text-muted-foreground">Empresas criadas (mês atual)</div>
            </div>
            <div className="text-right">
              <PctDelta pct={comparison.companiesDeltaPct} />
              <div className="text-xs text-muted-foreground">
                anterior: {comparison.companiesLastMonth.toLocaleString('pt-BR')}
              </div>
            </div>
          </div>
        </div>
      </Section>

      {/* Top empresas por volume */}
      <Section title="Top empresas por volume (30 dias)">
        <DataTable<TopTenant>
          data={topTenants}
          columns={topColumns}
          emptyMessage="Nenhuma empresa com mensagens nos últimos 30 dias."
        />
      </Section>

      {/* Empresas em risco */}
      <Section title="Empresas em risco (sem atividade há > 30 dias)">
        <DataTable<AtRiskTenant>
          data={atRisk}
          columns={riskColumns}
          emptyMessage="Nenhuma empresa em risco — todas com atividade recente."
        />
      </Section>

      {/* Crescimento (texto/lista, sem gráfico) */}
      <Section title="Crescimento (empresas criadas por mês)">
        <Card>
          <ul className="space-y-1 text-sm">
            {companiesCreatedPerMonth.map((m) => (
              <li key={m.month} className="flex items-center justify-between">
                <span className="text-muted-foreground tabular-nums">{m.month}</span>
                <span className="font-medium tabular-nums">{m.count.toLocaleString('pt-BR')}</span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-muted-foreground">
            Últimos 6 meses calendário. O gráfico visual fica para uma fase futura.
          </p>
        </Card>
      </Section>
    </div>
  )
}
