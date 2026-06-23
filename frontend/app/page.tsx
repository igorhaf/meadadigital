import { headers } from 'next/headers'
import Link from 'next/link'
import { redirect } from 'next/navigation'

import { SUBDOMAIN_HEADER, isUniversalSubdomain } from '@/lib/profiles/subdomain'

/**
 * Raiz do host.
 * - {nicho}.meadadigital.com/ → login do nicho (spec #1): subdomínio de nicho não tem landing,
 *   vai direto pro /login (que renderiza o branding daquele nicho).
 * - meadadigital.com/ (domínio-base, subdomínio 'meada') → LANDING pública do Meada (spec #3).
 *
 * O middleware injeta x-meada-subdomain. Subdomínio de empresa ({empresa}.meadadigital.com) é
 * tratado ANTES, no middleware (CMS ou redirect), e não chega aqui.
 */
export default async function Home() {
  const sub = (await headers()).get(SUBDOMAIN_HEADER) ?? 'meada'
  if (!isUniversalSubdomain(sub)) {
    redirect('/login')
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 text-center">
      <div className="space-y-4">
        <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">Meada</h1>
        <p className="mx-auto max-w-xl text-lg text-muted-foreground">
          Plataforma de atendimento com IA por WhatsApp para o seu negócio — um produto
          sob medida para cada nicho.
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
