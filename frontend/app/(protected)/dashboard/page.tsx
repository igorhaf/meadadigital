'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { ConversationsNavLink } from '@/components/conversations-nav-link'
import { OnboardingBanner } from '@/components/onboarding-banner'
import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import { getMyCompany } from '@/lib/supabase/companies'

/**
 * Hub do dashboard após login. Roteia por PAPEL:
 *  - super_admin → redireciona para /dashboard/companies (sua home funcional);
 *  - tenant_admin → placeholder "área restrita ao super-admin" (tela própria do tenant
 *    é 4.3+).
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
    return <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Carregando…</div>
  }

  if (isError) {
    console.error('failed to load /admin/me:', error)
    return (
      <div className="mx-auto max-w-3xl p-8">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-xl font-semibold">Dashboard</h1>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <SignOutButton />
          </div>
        </div>
        <p className="text-sm text-destructive">
          Erro ao carregar perfil. Tente sair e entrar de novo.
        </p>
      </div>
    )
  }

  if (isSuperAdmin) {
    // useEffect acima já disparou o replace; este render é só o tick intermediário.
    return <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
  }

  // tenant_admin
  return <TenantDashboard />
}

/**
 * Tela do tenant-admin: mostra os dados da PRÓPRIA empresa, lidos via Supabase SDK + RLS
 * (getMyCompany). O isolamento é do banco — o tenant nunca vê empresa de outro tenant.
 * CRUD de services/faqs/etc. e lista de usuários ficam para a 4.4.
 */
function TenantDashboard() {
  const { data: company, isPending, isError } = useQuery({
    queryKey: ['my-company'],
    queryFn: getMyCompany,
  })

  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Minha empresa</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard/services">
            <Button variant="outline">Serviços</Button>
          </Link>
          <Link href="/dashboard/faqs">
            <Button variant="outline">FAQs</Button>
          </Link>
          <ConversationsNavLink />
          <Link href="/dashboard/contacts">
            <Button variant="outline">Contatos</Button>
          </Link>
          <Link href="/dashboard/metrics">
            <Button variant="outline">Métricas</Button>
          </Link>
          <Link href="/dashboard/knowledge">
            <Button variant="outline">Conhecimento</Button>
          </Link>
          <Link href="/dashboard/tags">
            <Button variant="outline">Tags</Button>
          </Link>
          <Link href="/dashboard/business-hours">
            <Button variant="outline">Horários</Button>
          </Link>
          <Link href="/dashboard/ai-settings">
            <Button variant="outline">IA</Button>
          </Link>
          <Link href="/dashboard/team">
            <Button variant="outline">Equipe</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>

      {/* Onboarding (#46): banner discreto enquanto a empresa não configurou 3 dos 4
          passos. enabled = true aqui (TenantDashboard só renderiza para tenant_admin). */}
      <OnboardingBanner enabled />

      {isPending && <p className="text-sm text-muted-foreground">Carregando…</p>}

      {isError && (
        <p className="text-sm text-destructive">Erro ao carregar os dados da empresa.</p>
      )}

      {company && (
        <dl className="space-y-3 rounded-xl border border-border p-6">
          <div>
            <dt className="text-xs uppercase text-muted-foreground">Nome</dt>
            <dd className="text-sm font-medium">{company.name}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted-foreground">Slug</dt>
            <dd className="text-sm font-medium">{company.slug}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted-foreground">Status</dt>
            <dd className="text-sm font-medium">{company.status}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted-foreground">Criada em</dt>
            <dd className="text-sm font-medium">
              {new Date(company.createdAt).toLocaleString('pt-BR')}
            </dd>
          </div>
        </dl>
      )}
    </div>
  )
}
