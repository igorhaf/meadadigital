import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { fetchPageBySlug } from '@/lib/cms/public-fetch'

/**
 * Página pública interna do CMS por (slug, pageSlug) (SM-N): /p/{slug}/{pageSlug}. Server component,
 * sem shell, sem auth. 404 se a página/site não está publicado. navBase = /p/{slug}.
 */
export default async function PublicPage({
  params,
}: {
  params: Promise<{ slug: string; pageSlug: string }>
}) {
  const { slug, pageSlug } = await params
  const view = await fetchPageBySlug(slug, pageSlug)
  if (!view) {
    notFound()
  }
  return (
    <CmsRender
      title={view.title}
      blocks={view.blocks}
      theme={view.theme}
      nav={view.nav}
      navBase={`/p/${slug}`}
    />
  )
}
