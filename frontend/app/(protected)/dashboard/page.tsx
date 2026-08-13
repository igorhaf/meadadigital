'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { getMe } from '@/lib/api/me'

/**
 * Home do painel — boas-vindas + atalhos para a configuração da IA do tenant.
 * A identidade (empresa, papel, produto) vem do GET /admin/me.
 */
export default function DashboardHomePage() {
  const { data: me, isPending } = useQuery({ queryKey: ['me'], queryFn: getMe })

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Bem-vindo"
        description="Seu atendente de IA responde seus clientes no WhatsApp com os dados que você configurar aqui."
      />
      {me?.role === 'super_admin' ? (
        <Card>
          <Section
            title="Console da plataforma"
            description="Você está logado como super-admin. A operação de empresas vive na API de admin nesta fase."
          >
            <p className="text-sm text-muted-foreground">
              Ferramentas visuais de plataforma chegam nas próximas camadas.
            </p>
          </Section>
        </Card>
      ) : (
        <Card>
          <Section
            title="Configure sua IA"
            description="Quanto melhor o contexto, melhor a resposta. Comece pelos quatro blocos abaixo."
          >
            <div className="grid gap-3 sm:grid-cols-2">
              <Link href="/dashboard/ai-settings">
                <Button variant="outline" className="w-full justify-start">
                  Tom e regras da IA
                </Button>
              </Link>
              <Link href="/dashboard/faqs">
                <Button variant="outline" className="w-full justify-start">
                  FAQs
                </Button>
              </Link>
              <Link href="/dashboard/services">
                <Button variant="outline" className="w-full justify-start">
                  Serviços e preços
                </Button>
              </Link>
              <Link href="/dashboard/business-hours">
                <Button variant="outline" className="w-full justify-start">
                  Horários de funcionamento
                </Button>
              </Link>
            </div>
          </Section>
        </Card>
      )}
    </div>
  )
}
