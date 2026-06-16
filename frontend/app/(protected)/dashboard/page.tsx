'use client'

import { useQuery } from '@tanstack/react-query'
import { BarChart3, CalendarDays, MessagesSquare, Users } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, type ComponentType } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { OnboardingBanner } from '@/components/onboarding-banner'
import { Card } from '@/components/ui/card'
import { PageSkeleton } from '@/components/ui/skeleton'
import { getMe } from '@/lib/api/me'
import { getMyCompany } from '@/lib/supabase/companies'

/**
 * Hub do dashboard após login. Roteia por PAPEL:
 *  - super_admin → redireciona para /dashboard/companies (sua home funcional);
 *  - tenant_admin → hub de "Início" com atalhos rápidos para as telas de atendimento.
 *
 * O redirect usa useEffect + router.replace (NÃO no render): chamar router.replace
 * durante o render quebra ("Cannot update a component while rendering") — o useEffect
 * agenda para o próximo tick. replace (não push) evita que o "voltar" do browser caia
 * de novo em /dashboard e gere loop de redirect.
 */
export default function DashboardPage() {
  const router = useRouter()
  const { data: me, isPending, isError, error } = useQuery({ queryKey: ['me'], queryFn: getMe })

  const isSuperAdmin = me?.role === 'super_admin'

  useEffect(() => {
    // Só dispara quando me chegou e é super-admin. Durante isPending/isError,
    // isSuperAdmin é false (me undefined) — useEffect roda mas não age.
    if (isSuperAdmin) {
      router.replace('/dashboard/companies')
    }
  }, [isSuperAdmin, router])

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

  if (isSuperAdmin) {
    // useEffect acima já disparou o replace; este render é só o tick intermediário.
    return <p className="text-sm text-muted-foreground">Redirecionando…</p>
  }

  // tenant_admin
  return <TenantDashboard />
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
