import type {
  BannerStripProps,
  CmsBlock,
  ColumnsProps,
  ContactProps,
  CtaProps,
  FaqProps,
  FeatureGridProps,
  GalleryProps,
  HeroProps,
  ImageTextSplitProps,
  MapProps,
  MarqueeProps,
  MeadaHeroProps,
  PackagesProps,
  QuoteProps,
  ServicesProps,
  StatsProps,
  StepsProps,
  TestimonialsProps,
  TextProps,
} from '@/lib/cms/cms-block-type'
import type { CmsNavItem, CmsTheme } from '@/lib/cms/public-fetch'
import { MeadaHero } from '@/components/cms/blocks/meada-hero'
import { MeadaCta, MeadaPortfolio, MeadaServices } from '@/components/cms/blocks/meada-sections'

/**
 * Renderizador do CMS (SM-N / catálogo ampliado). Renderiza os 18 tipos de bloco. Tema: cor primária
 * (CSS var `--cms-primary`) + modo claro/escuro. Os blocos usam `var(--cms-primary)` e tons neutros
 * (sem cor hardcoded) pra herdar o tema do tenant.
 *
 * ⚠️ CLIENT-SAFE — este arquivo é importado TANTO pelo server (CmsRender em /p/) QUANTO pelo editor
 * client (cms-block-canvas usa `renderCmsBlock`/`cmsShellStyle` no preview ao vivo). NÃO importar
 * `next/headers`, `cookies()`, `next/image` ou qualquer API server-only aqui — quebraria o editor.
 *
 * navBase: base das URLs de nav. Em /p/{slug} é "/p/{slug}"; sob domínio custom é "" (raiz),
 * porque o middleware reescreve a raiz pro tenant.
 */

// ---- helpers ----------------------------------------------------------------

/** Estilo-casca do site: injeta --cms-primary + fundo/cor conforme o tema. Compartilhado entre o
 * render público (CmsRender) e o preview do editor — garante que o preview bate com o /p/.
 *
 * preset 'meada-dark': a identidade da marca Meada — fundo near-black #000812, texto claro, Geist,
 * e a var --cms-gradient (azul→roxo→rosa) que os blocos meada_* usam. Sem preset = tema genérico
 * (primaryColor + dark), retrocompatível: nenhum site existente muda. */
export function cmsShellStyle(theme: CmsTheme | null): React.CSSProperties {
  if (theme?.preset === 'meada-dark') {
    return {
      ['--cms-primary' as string]: '#3b82f6',
      ['--cms-gradient' as string]: 'linear-gradient(125deg, #60a5fa 0%, #a855f7 50%, #ec4899 100%)',
      background: '#000812',
      color: '#ffffff',
      fontFamily: 'var(--font-geist-sans), ui-sans-serif, system-ui, sans-serif',
    }
  }
  const primary = theme?.primaryColor || '#0f172a'
  const dark = theme?.dark === true
  return {
    ['--cms-primary' as string]: primary,
    background: dark ? '#0b1120' : '#ffffff',
    color: dark ? '#e2e8f0' : '#0f172a',
  }
}

// ---- blocos originais (8) ---------------------------------------------------

function HeroBlock({ props }: { props: HeroProps }) {
  return (
    <section className="relative overflow-hidden px-6 py-20 text-center" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <div className="mx-auto grid max-w-6xl items-center gap-10 md:grid-cols-2 md:text-left">
        <div>
          {props.badge && (
            <span className="inline-block rounded-full bg-white/15 px-3 py-1 text-xs font-medium uppercase tracking-widest">
              {props.badge}
            </span>
          )}
          {props.title && <h1 className="mt-4 text-4xl font-bold tracking-tight md:text-5xl">{props.title}</h1>}
          {props.subtitle && <p className="mx-auto mt-4 max-w-2xl text-lg opacity-90 md:mx-0">{props.subtitle}</p>}
          <div className="mt-8 flex flex-wrap justify-center gap-3 md:justify-start">
            {props.buttonLabel && props.buttonHref && (
              <a href={props.buttonHref} className="inline-block rounded-md bg-white px-6 py-3 font-medium text-slate-900 hover:bg-slate-100">
                {props.buttonLabel}
              </a>
            )}
            {props.secondaryButtonLabel && props.secondaryButtonHref && (
              <a href={props.secondaryButtonHref} className="inline-block rounded-md px-6 py-3 font-medium text-white ring-1 ring-white/40 hover:bg-white/10">
                {props.secondaryButtonLabel}
              </a>
            )}
          </div>
        </div>
        {props.imageUrl && (
          /* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */
          <img src={props.imageUrl} alt={props.title || ''} className="w-full rounded-3xl shadow-2xl md:aspect-[4/3] md:object-cover" />
        )}
      </div>
    </section>
  )
}

function TextBlock({ props }: { props: TextProps }) {
  const paragraphs = (props.body ?? '').split(/\n\s*\n/).filter((p) => p.trim() !== '')
  return (
    <section className="mx-auto max-w-3xl px-6 py-12">
      {paragraphs.map((p, i) => (
        <p key={i} className="mb-4 whitespace-pre-line leading-relaxed">{p}</p>
      ))}
    </section>
  )
}

function ServicesBlock({ props }: { props: ServicesProps }) {
  return (
    <section className="mx-auto max-w-4xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
      <div className="grid gap-6 sm:grid-cols-2">
        {(props.items ?? []).map((it, i) => (
          <div key={i} className="rounded-lg border border-black/10 p-5">
            <div className="flex items-baseline justify-between gap-3">
              <h3 className="font-semibold">{it.name}</h3>
              {it.price && <span className="shrink-0 text-sm opacity-70">{it.price}</span>}
            </div>
            {it.description && <p className="mt-2 text-sm opacity-80">{it.description}</p>}
          </div>
        ))}
      </div>
    </section>
  )
}

function ContactBlock({ props }: { props: ContactProps }) {
  const waHref = props.whatsapp ? `https://wa.me/${props.whatsapp.replace(/\D/g, '')}` : null
  return (
    <section className="px-6 py-12" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-2xl text-center">
        <h2 className="mb-6 text-2xl font-bold">Contato</h2>
        <dl className="space-y-2">
          {props.phone && <dd>{props.phone}</dd>}
          {props.address && <dd>{props.address}</dd>}
          {props.hours && <dd>{props.hours}</dd>}
        </dl>
        {waHref && (
          <a href={waHref} className="mt-6 inline-block rounded-md bg-emerald-600 px-6 py-3 font-medium text-white hover:bg-emerald-700">
            Falar no WhatsApp
          </a>
        )}
      </div>
    </section>
  )
}

function GalleryBlock({ props }: { props: GalleryProps }) {
  return (
    <section className="mx-auto max-w-5xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
        {(props.images ?? []).map((img, i) => (
          <figure key={i} className="overflow-hidden rounded-lg border border-black/10">
            {/* eslint-disable-next-line @next/next/no-img-element -- URLs externas coladas pelo tenant */}
            <img src={img.url} alt={img.caption || ''} className="h-48 w-full object-cover" />
            {img.caption && <figcaption className="px-3 py-2 text-sm opacity-70">{img.caption}</figcaption>}
          </figure>
        ))}
      </div>
    </section>
  )
}

function FaqBlock({ props }: { props: FaqProps }) {
  return (
    <section className="mx-auto max-w-3xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
      <div className="space-y-3">
        {(props.items ?? []).map((it, i) => (
          <details key={i} className="rounded-lg border border-black/10 p-4">
            <summary className="cursor-pointer font-medium">{it.question}</summary>
            <p className="mt-2 whitespace-pre-line opacity-80">{it.answer}</p>
          </details>
        ))}
      </div>
    </section>
  )
}

function TestimonialsBlock({ props }: { props: TestimonialsProps }) {
  return (
    <section className="px-6 py-12" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-4xl">
        {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
        <div className="grid gap-6 sm:grid-cols-2">
          {(props.items ?? []).map((t, i) => (
            <blockquote key={i} className="rounded-lg border border-black/10 bg-white/60 p-5">
              <p className="italic">“{t.text}”</p>
              <footer className="mt-3 text-sm font-medium">
                {t.name}{t.rating && <span className="ml-2 opacity-70">{t.rating}</span>}
              </footer>
            </blockquote>
          ))}
        </div>
      </div>
    </section>
  )
}

function MapBlock({ props }: { props: MapProps }) {
  return (
    <section className="mx-auto max-w-4xl px-6 py-12">
      {props.title && <h2 className="mb-4 text-center text-2xl font-bold">{props.title}</h2>}
      {props.address && <p className="mb-4 text-center opacity-80">{props.address}</p>}
      {props.embedUrl && (
        <div className="overflow-hidden rounded-lg border border-black/10">
          <iframe src={props.embedUrl} title="Mapa" className="h-80 w-full" loading="lazy" />
        </div>
      )}
    </section>
  )
}

// ---- catálogo ampliado (10) — re-tematizado pra var(--cms-primary) + tons neutros ----

function BannerStripBlock({ props }: { props: BannerStripProps }) {
  if (!props.message) return null
  return (
    <div className="px-6 py-3 text-center text-sm" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <span className="font-medium">{props.message}</span>
      {props.buttonLabel && props.buttonHref && (
        <a href={props.buttonHref} className="ml-3 font-semibold underline underline-offset-2 hover:no-underline">
          {props.buttonLabel} →
        </a>
      )}
    </div>
  )
}

function StatsBlock({ props }: { props: StatsProps }) {
  return (
    <section style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <div className="mx-auto grid max-w-6xl grid-cols-2 gap-6 px-6 py-12 md:grid-cols-4">
        {(props.items ?? []).map((it, i) => (
          <div key={i} className="text-center">
            <div className="text-3xl font-bold md:text-4xl">{it.value}</div>
            <div className="mt-1 text-xs uppercase tracking-widest opacity-80 md:text-sm">{it.label}</div>
          </div>
        ))}
      </div>
    </section>
  )
}

function FeatureGridBlock({ props }: { props: FeatureGridProps }) {
  return (
    <section className="mx-auto max-w-6xl px-6 py-16">
      {(props.title || props.eyebrow) && (
        <div className="mb-10 max-w-2xl">
          {props.eyebrow && <div className="text-xs uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>{props.eyebrow}</div>}
          {props.title && <h2 className="mt-2 text-3xl font-bold md:text-4xl">{props.title}</h2>}
        </div>
      )}
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {(props.items ?? []).map((it, i) => (
          <div key={i} className="rounded-2xl border border-black/10 p-6 shadow-sm">
            {it.icon && <div className="mb-3 text-3xl">{it.icon}</div>}
            <h3 className="font-semibold">{it.title}</h3>
            <p className="mt-1 text-sm opacity-70">{it.description}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function ImageTextSplitBlock({ props }: { props: ImageTextSplitProps }) {
  const reverse = props.reverse === true
  return (
    <section className="mx-auto max-w-6xl px-6 py-16">
      <div className={`grid items-center gap-12 md:grid-cols-2 ${reverse ? 'md:[direction:rtl]' : ''}`}>
        <div className="md:[direction:ltr]">
          {props.eyebrow && <div className="text-xs uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>{props.eyebrow}</div>}
          {props.title && <h2 className="mt-2 text-3xl font-bold md:text-4xl">{props.title}</h2>}
          {props.body && <p className="mt-4 whitespace-pre-line opacity-80">{props.body}</p>}
          {props.buttonLabel && props.buttonHref && (
            <a href={props.buttonHref} className="mt-6 inline-block rounded-md px-6 py-3 font-medium text-white" style={{ background: 'var(--cms-primary)' }}>
              {props.buttonLabel}
            </a>
          )}
        </div>
        {props.imageUrl && (
          <div className="md:[direction:ltr]">
            {/* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */}
            <img src={props.imageUrl} alt={props.title || ''} className="aspect-[4/3] w-full rounded-3xl object-cover shadow-xl" />
          </div>
        )}
      </div>
    </section>
  )
}

function StepsBlock({ props }: { props: StepsProps }) {
  return (
    <section className="px-6 py-16" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-6xl">
        {(props.title || props.eyebrow) && (
          <div className="mx-auto mb-12 max-w-2xl text-center">
            {props.eyebrow && <div className="text-xs uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>{props.eyebrow}</div>}
            {props.title && <h2 className="mt-2 text-3xl font-bold md:text-4xl">{props.title}</h2>}
          </div>
        )}
        <div className="grid gap-6 md:grid-cols-3">
          {(props.items ?? []).map((step, i) => (
            <div key={i} className="rounded-2xl border border-black/10 bg-white/60 p-6">
              <div className="text-5xl font-black" style={{ color: 'var(--cms-primary)' }}>{step.number || `0${i + 1}`}</div>
              <h3 className="mt-3 font-semibold">{step.title}</h3>
              <p className="mt-2 text-sm opacity-70">{step.description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function ColumnsBlock({ props }: { props: ColumnsProps }) {
  const items = props.items ?? []
  const colsClass = items.length === 4 ? 'md:grid-cols-4' : items.length === 2 ? 'md:grid-cols-2' : 'md:grid-cols-3'
  return (
    <section className="mx-auto max-w-6xl px-6 py-16">
      {(props.title || props.eyebrow) && (
        <div className="mb-10 max-w-2xl">
          {props.eyebrow && <div className="text-xs uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>{props.eyebrow}</div>}
          {props.title && <h2 className="mt-2 text-3xl font-bold md:text-4xl">{props.title}</h2>}
        </div>
      )}
      <div className={`grid gap-5 ${colsClass}`}>
        {items.map((it, i) => (
          <div key={i} className="rounded-2xl border border-black/10 p-6">
            {it.icon && <div className="text-3xl">{it.icon}</div>}
            <h3 className="mt-3 font-semibold">{it.title}</h3>
            <p className="mt-2 text-sm opacity-70">{it.body}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function PackagesBlock({ props }: { props: PackagesProps }) {
  return (
    <section className="px-6 py-16" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-6xl">
        {(props.title || props.eyebrow) && (
          <div className="mb-10 max-w-2xl">
            {props.eyebrow && <div className="text-xs uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>{props.eyebrow}</div>}
            {props.title && <h2 className="mt-2 text-3xl font-bold md:text-4xl">{props.title}</h2>}
            {props.subtitle && <p className="mt-2 opacity-70">{props.subtitle}</p>}
          </div>
        )}
        <div className="grid gap-6 md:grid-cols-3">
          {(props.items ?? []).map((p, i) => (
            <div key={i} className={`overflow-hidden rounded-2xl bg-white shadow-sm ${p.popular ? 'ring-2' : 'ring-1 ring-black/10'}`}
              style={p.popular ? { ['--tw-ring-color' as string]: 'var(--cms-primary)' } : undefined}>
              {p.imageUrl && (
                /* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */
                <img src={p.imageUrl} alt={p.name} className="aspect-[4/3] w-full object-cover" />
              )}
              <div className="p-6 text-slate-900">
                {p.popular && <div className="mb-2 text-xs font-semibold uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>Mais escolhido</div>}
                <h3 className="text-xl font-bold">{p.name}</h3>
                {p.description && <p className="mt-2 text-sm opacity-70">{p.description}</p>}
                {p.price && <div className="mt-4 text-3xl font-bold tabular-nums">{p.price}</div>}
                {p.buttonLabel && (
                  <a href={p.buttonHref || '#'} className="mt-6 inline-block w-full rounded-xl py-2.5 text-center font-semibold text-white" style={{ background: 'var(--cms-primary)' }}>
                    {p.buttonLabel}
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function MarqueeBlock({ props }: { props: MarqueeProps }) {
  const items = props.items ?? []
  if (items.length === 0) return null
  return (
    <section style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-6 gap-y-2 px-6 py-6 text-sm">
        {props.label && <span className="text-xs uppercase tracking-widest opacity-70">{props.label}</span>}
        {items.map((it, i) => (
          <span key={i} className="inline-flex items-center gap-2 font-medium tracking-tight">
            {it.name}
            {i < items.length - 1 && <span className="opacity-40">✦</span>}
          </span>
        ))}
      </div>
    </section>
  )
}

function QuoteBlock({ props }: { props: QuoteProps }) {
  if (!props.text) return null
  return (
    <section className="mx-auto max-w-4xl px-6 py-16 text-center">
      <p className="text-2xl font-medium leading-snug md:text-3xl">“{props.text}”</p>
      {props.author && (
        <div className="mt-6 text-sm opacity-70">
          <div className="font-semibold opacity-100">{props.author}</div>
          {props.role && <div>{props.role}</div>}
        </div>
      )}
    </section>
  )
}

function CtaBlock({ props }: { props: CtaProps }) {
  return (
    <section className="px-6 py-16 text-center" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <div className="mx-auto max-w-4xl">
        {props.title && <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{props.title}</h2>}
        {props.subtitle && <p className="mx-auto mt-4 max-w-2xl opacity-90">{props.subtitle}</p>}
        {props.buttonLabel && props.buttonHref && (
          <a href={props.buttonHref} className="mt-8 inline-block rounded-md bg-white px-8 py-3 font-semibold text-slate-900 hover:bg-slate-100">
            {props.buttonLabel}
          </a>
        )}
      </div>
    </section>
  )
}

// ---- registry + dispatch ----------------------------------------------------

/** Mapa type → componente. Usado pelo dispatch (render público) e exposto pro editor. */
export const blockComponents = {
  hero: HeroBlock,
  text: TextBlock,
  services: ServicesBlock,
  contact: ContactBlock,
  gallery: GalleryBlock,
  faq: FaqBlock,
  testimonials: TestimonialsBlock,
  map: MapBlock,
  banner_strip: BannerStripBlock,
  stats: StatsBlock,
  feature_grid: FeatureGridBlock,
  image_text_split: ImageTextSplitBlock,
  steps: StepsBlock,
  columns: ColumnsBlock,
  packages: PackagesBlock,
  marquee: MarqueeBlock,
  quote: QuoteBlock,
  cta: CtaBlock,
  meada_hero: MeadaHero,
  meada_services: MeadaServices,
  meada_portfolio: MeadaPortfolio,
  meada_cta: MeadaCta,
} as const

/** Renderiza um bloco (com key = block.id). Compartilhado entre CmsRender (público) e o editor. */
export function renderCmsBlock(b: CmsBlock): React.ReactElement | null {
  switch (b.type) {
    case 'hero': return <HeroBlock key={b.id} props={b.props} />
    case 'text': return <TextBlock key={b.id} props={b.props} />
    case 'services': return <ServicesBlock key={b.id} props={b.props} />
    case 'contact': return <ContactBlock key={b.id} props={b.props} />
    case 'gallery': return <GalleryBlock key={b.id} props={b.props} />
    case 'faq': return <FaqBlock key={b.id} props={b.props} />
    case 'testimonials': return <TestimonialsBlock key={b.id} props={b.props} />
    case 'map': return <MapBlock key={b.id} props={b.props} />
    case 'banner_strip': return <BannerStripBlock key={b.id} props={b.props} />
    case 'stats': return <StatsBlock key={b.id} props={b.props} />
    case 'feature_grid': return <FeatureGridBlock key={b.id} props={b.props} />
    case 'image_text_split': return <ImageTextSplitBlock key={b.id} props={b.props} />
    case 'steps': return <StepsBlock key={b.id} props={b.props} />
    case 'columns': return <ColumnsBlock key={b.id} props={b.props} />
    case 'packages': return <PackagesBlock key={b.id} props={b.props} />
    case 'marquee': return <MarqueeBlock key={b.id} props={b.props} />
    case 'quote': return <QuoteBlock key={b.id} props={b.props} />
    case 'cta': return <CtaBlock key={b.id} props={b.props} />
    case 'meada_hero': return <MeadaHero key={b.id} props={b.props} />
    case 'meada_services': return <MeadaServices key={b.id} props={b.props} />
    case 'meada_portfolio': return <MeadaPortfolio key={b.id} props={b.props} />
    case 'meada_cta': return <MeadaCta key={b.id} props={b.props} />
    default: return null
  }
}

export function CmsRender({
  title,
  blocks,
  theme,
  nav,
  navBase,
}: {
  title: string
  blocks: CmsBlock[]
  theme: CmsTheme | null
  nav: CmsNavItem[]
  navBase: string
}) {
  return (
    <main className="min-h-screen" style={cmsShellStyle(theme)}>
      {nav.length > 1 && (
        <nav className="flex flex-wrap items-center justify-center gap-4 border-b border-black/10 px-6 py-4 text-sm">
          {nav.map((n) => (
            <a key={n.pageSlug} href={n.isHome ? `${navBase || '/'}` : `${navBase}/${n.pageSlug}`} className="hover:underline">
              {n.title || n.pageSlug}
            </a>
          ))}
        </nav>
      )}
      {blocks.map(renderCmsBlock)}
      {blocks.length === 0 && (
        <div className="flex min-h-[50vh] items-center justify-center opacity-50">
          <p>{title || 'Página em construção.'}</p>
        </div>
      )}
    </main>
  )
}
