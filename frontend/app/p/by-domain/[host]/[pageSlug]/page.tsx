import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { fetchPageByDomain } from '@/lib/cms/public-fetch'

/**
 * Página interna pública por DOMÍNIO custom + pageSlug (SM-N). Destino do rewrite do middleware
 * quando, sob um domínio próprio, o visitante acessa /{pageSlug}. navBase = "" (raiz do domínio).
 */
export default async function PublicByDomainPage({
  params,
}: {
  params: Promise<{ host: string; pageSlug: string }>
}) {
  const { host, pageSlug } = await params
  const view = await fetchPageByDomain(host, pageSlug)
  if (!view) {
    notFound()
  }
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
