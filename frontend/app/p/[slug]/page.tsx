import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { fetchHomeBySlug } from '@/lib/cms/public-fetch'

/**
 * Home pública do CMS por slug do tenant (SM-N): /p/{slug}. Server component, sem shell, sem auth.
 * 404 se o site/página home não está publicado. navBase = /p/{slug} (links de nav).
 */
export default async function PublicHome({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const view = await fetchHomeBySlug(slug)
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
