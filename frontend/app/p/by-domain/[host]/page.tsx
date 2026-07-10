import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { fetchHomeByDomain } from '@/lib/cms/public-fetch'

/**
 * Home pública por DOMÍNIO custom VERIFICADO (SM-N). Destino interno do rewrite do middleware quando
 * o host não é do Meada: /p/by-domain/{host}. Resolve o tenant pelo domínio (verificado + publicado).
 * navBase = "" (raiz) — sob o domínio próprio, as páginas internas respondem em /{pageSlug} via o
 * mesmo rewrite do middleware.
 */
export default async function PublicByDomain({ params }: { params: Promise<{ host: string }> }) {
  const { host } = await params
  const view = await fetchHomeByDomain(host)
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
