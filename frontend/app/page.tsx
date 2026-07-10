import { headers } from 'next/headers'
import Link from 'next/link'
import { redirect } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { MEADA_INSTITUTIONAL_SLUG } from '@/lib/cms/meada-institutional'
import { fetchHomeBySlug } from '@/lib/cms/public-fetch'
import { isUniversalSubdomain, SUBDOMAIN_HEADER } from '@/lib/profiles/subdomain'

/**
 * Raiz do host.
 * - {nicho}.meadadigital.com/ → login do nicho (spec #1).
 * - meadadigital.com/ (domínio-base) → CMS do tenant institucional do Meada (editado no painel,
 *   igual aos tenants). Se esse CMS ainda não estiver publicado, cai numa landing mínima de
 *   fallback (a raiz nunca fica quebrada).
 *
 * Subdomínio de empresa ({empresa}.meadadigital.com) é tratado ANTES, no middleware.
 */
export default async function Home() {
  const sub = (await headers()).get(SUBDOMAIN_HEADER) ?? 'meada'
  if (!isUniversalSubdomain(sub)) {
    redirect('/login')
  }

  // Domínio-base → serve o CMS institucional do Meada (mesma maquinaria dos tenants).
  const view = await fetchHomeBySlug(MEADA_INSTITUTIONAL_SLUG)
  if (view) {
    return (
      <CmsRender
        title={view.title}
        blocks={view.blocks}
        theme={view.theme}
        nav={view.nav}
        navBase=""
      />
    )
  }

  // Fallback: CMS institucional ainda não publicado.
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 text-center">
      <div className="space-y-4">
        <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">Meada</h1>
        <p className="mx-auto max-w-xl text-lg text-muted-foreground">
          Plataforma de atendimento com IA por WhatsApp para o seu negócio — um produto sob medida
          para cada nicho.
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          href="/login"
          className="inline-flex h-11 items-center justify-center rounded-md bg-primary px-6 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
        >
          Entrar no painel
        </Link>
      </div>
    </main>
  )
}
