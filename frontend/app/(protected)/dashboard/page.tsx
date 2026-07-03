'use client'

import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, BarChart3, CalendarDays, MessagesSquare, Users } from 'lucide-react'
import Link from 'next/link'
import { type ComponentType } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { OnboardingBanner } from '@/components/onboarding-banner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { PageSkeleton } from '@/components/ui/skeleton'
import { getAdminOverview } from '@/lib/api/admin/dashboard'
import { getMe } from '@/lib/api/me'
import { getMyCompany } from '@/lib/supabase/companies'

/**
 * Hub do dashboard após login. Roteia por PAPEL:
 *  - super_admin → AdminDashboard (KPIs da plataforma — camada 6.0; antes era redirect
 *    para /dashboard/companies, removido nesta fase);
 *  - tenant_admin → hub de "Início" com atalhos rápidos para as telas de atendimento.
 */
export default function DashboardPage() {
  const { data: me, isPending, isError, error } = useQuery({ queryKey: ['me'], queryFn: getMe })

  if (isPending) {
    return <PageSkeleton />
  }

  if (isError) {
    console.error('failed to load /admin/me:', error)
    return (
      <div className="space-y-6">
        <PageHeader title="Início" />
        <p className="text-sm text-destructive">
          Erro ao carregar perfil. Tente sair e entrar de novo.
        </p>
      </div>
    )
  }

  if (me?.role === 'super_admin') {
    return <AdminDashboard />
  }

  // tenant_admin
  return <TenantDashboard />
}

/** Formata número grande de forma compacta (1.2M, 34k). pt-BR. */
function compact(n: number): string {
  return new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

/** Delta percentual hoje vs ontem, com sinal e cor. */
function DeltaLabel({ today, yesterday }: { today: number; yesterday: number }) {
  if (yesterday === 0) {
    return <span className="text-muted-foreground">sem base de ontem</span>
  }
  const pct = Math.round(((today - yesterday) / yesterday) * 100)
  const up = pct >= 0
  return (
    <span className={up ? 'text-green-600' : 'text-destructive'}>
      {up ? '+' : ''}
      {pct}% vs ontem
    </span>
  )
}

/**
 * Hub do super-admin (camada 6.0): KPIs da plataforma + banner de alertas. Atualiza a cada
 * 30s (refetchInterval). Loading = PageSkeleton; erro = texto + "Tentar novamente".
 */
function AdminDashboard() {
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: getAdminOverview,
    refetchInterval: 30_000,
  })

  if (isPending) {
    return <PageSkeleton />
  }

  if (isError || !data) {
    return (
      <div className="space-y-6">
        <PageHeader title="Início" description="Visão geral da plataforma Meada" />
        <p className="text-sm text-destructive">Erro ao carregar a visão geral da plataforma.</p>
        <Button variant="outline" onClick={() => refetch()}>
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Início"
        description="Visão geral da plataforma Meada"
        actions={
          <Link href="/dashboard/metrics">
            <Button variant="outline">Ver métricas detalhadas</Button>
          </Link>
        }
      />

      {data.alerts.length > 0 && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-4">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 size-5 shrink-0 text-destructive" />
            <div className="space-y-1">
              {data.alerts.map((a, i) => (
                <p key={i} className="text-sm text-foreground">
                  {a.message}
                </p>
              ))}
              <Link
                href="/dashboard/health"
                className="inline-block text-sm font-medium text-destructive hover:underline"
              >
                Ver detalhes
              </Link>
            </div>
          </div>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <p className="text-sm text-muted-foreground">Empresas ativas</p>
          <p className="mt-1 text-3xl font-semibold">{data.activeCompanies}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            +{data.companiesCreatedThisMonth} este mês
          </p>
        </Card>
        <Card>
          <p className="text-sm text-muted-foreground">Mensagens hoje</p>
          <p className="mt-1 text-3xl font-semibold">{data.messagesToday}</p>
          <p className="mt-1 text-xs">
            <DeltaLabel today={data.messagesToday} yesterday={data.messagesYesterday} />
          </p>
        </Card>
        <Card>
          <p className="text-sm text-muted-foreground">Conversas abertas</p>
          <p className="mt-1 text-3xl font-semibold">{data.openConversations}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            em {data.openConversationsCompanyCount} empresa(s)
          </p>
        </Card>
        <Card>
          <p className="text-sm text-muted-foreground">Tokens Gemini (mês)</p>
          <p className="mt-1 text-3xl font-semibold">{compact(data.geminiTokensThisMonth)}</p>
          <p className="mt-1 text-xs text-muted-foreground">consumo estimado</p>
        </Card>
      </div>
    </div>
  )
}

/** Atalhos rápidos do hub — cada um leva a uma tela cheia da área de atendimento. */
const SHORTCUTS: {
  href: string
  title: string
  description: string
  icon: ComponentType<{ className?: string }>
}[] = [
  {
    href: '/dashboard/conversations',
    title: 'Conversas',
    description: 'Acompanhe e responda os atendimentos em andamento.',
    icon: MessagesSquare,
  },
  {
    href: '/dashboard/calendar',
    title: 'Agenda',
    description: 'Veja os horários e compromissos agendados.',
    icon: CalendarDays,
  },
  {
    href: '/dashboard/contacts',
    title: 'Contatos',
    description: 'Gerencie as pessoas que falam com a sua empresa.',
    icon: Users,
  },
  {
    href: '/dashboard/metrics',
    title: 'Métricas',
    description: 'Volume de conversas, FAQs e tempo médio de resposta.',
    icon: BarChart3,
  },
]

/**
 * Tela do tenant-admin: hub "Início" com visão geral e atalhos rápidos. Os dados da
 * empresa são lidos via Supabase SDK + RLS (getMyCompany) só para personalizar o
 * subtítulo — o isolamento é do banco, o tenant nunca vê empresa de outro tenant.
 */
function TenantDashboard() {
  const { data: company } = useQuery({
    queryKey: ['my-company'],
    queryFn: getMyCompany,
  })

  const description = company
    ? `Visão geral do atendimento de ${company.name}`
    : 'Visão geral do seu atendimento'

  return (
    <div className="space-y-6">
      <PageHeader title="Início" description={description} />

      {/* Onboarding (#46): banner discreto enquanto a empresa não configurou 3 dos 4
          passos. enabled = true aqui (TenantDashboard só renderiza para tenant_admin). */}
      <OnboardingBanner enabled />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {SHORTCUTS.map((s) => {
          const Icon = s.icon
          return (
            <Link key={s.href} href={s.href} className="block">
              <Card className="h-full transition-colors hover:border-foreground/20 hover:bg-muted/40">
                <Icon className="mb-3 h-5 w-5 text-muted-foreground" />
                <h2 className="text-base font-semibold text-foreground">{s.title}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{s.description}</p>
              </Card>
            </Link>
          )
        })}
      </div>
    </div>
  )
}
