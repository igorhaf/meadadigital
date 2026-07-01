import './cms-theme.css'
import { cn } from '@/lib/utils'
import { slotRing } from '@/lib/cms/slot-highlight'
import type {
  BannerStripProps,
  CmsBlock,
  CmsColumnWidth,
  CmsRow,
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
import { safeUrl, safeFrameSrc } from '@/lib/cms/safe-url'
import { themeById } from '@/lib/cms/themes/theme-catalog'
import { themeToCssVars, themeLayoutAttrs } from '@/lib/cms/themes/theme-tokens'
import { MeadaHero } from '@/components/cms/blocks/meada-hero'
import { MeadaCta, MeadaPortfolio, MeadaServices } from '@/components/cms/blocks/meada-sections'
import { MeadaFooter, MeadaNavbar } from '@/components/cms/blocks/meada-chrome'
import { NichesGrid } from '@/components/cms/blocks/niches-grid'

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
  // Tema do CATÁLOGO (sistema de temas por nicho): resolve paleta+forma completas via themeId.
  if (theme?.themeId) {
    const def = themeById(theme.themeId)
    if (def) return themeToCssVars(def)
  }
  const primary = theme?.primaryColor || '#0f172a'
  const dark = theme?.dark === true
  return {
    ['--cms-primary' as string]: primary,
    background: dark ? '#0b1120' : '#ffffff',
    color: dark ? '#e2e8f0' : '#0f172a',
  }
}

/** data-attributes de layout (hero/card/dark) pro shell quando o tema é do catálogo — os blocos
 * leem via seletores `[data-hero=split]`, `[data-card=shadow]`, etc. {} se não há themeId. */
export function cmsShellLayoutAttrs(theme: CmsTheme | null): Record<string, string> {
  if (theme?.themeId) {
    const def = themeById(theme.themeId)
    if (def) return themeLayoutAttrs(def)
  }
  return {}
}

// ---- blocos originais (8) ---------------------------------------------------

/** activeSlot: SÓ no editor (via renderCmsBlock opts). Quando casa com a sub-parte, desenha um ring nela
 * (destaque hierárquico). No /p/ público é undefined → ring nunca aparece; data-slot é atributo inerte
 * (não muda layout/CSS), então o render visível é idêntico ao de produção. */
function HeroBlock({ props, activeSlot }: { props: HeroProps; activeSlot?: string }) {
  // anel de destaque pra sub-parte ativa — helper compartilhado; fundo do hero é primário → ring branco.
  const ringIf = (slot: string) => slotRing(activeSlot, slot)
  return (
    <section className="relative overflow-hidden px-6 py-20 text-center" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <div className="mx-auto grid max-w-6xl items-center gap-10 md:grid-cols-2 md:text-left">
        <div data-slot="content" className={cn(ringIf('content'))}>
          {props.badge && (
            <span className="inline-block rounded-full bg-white/15 px-3 py-1 text-xs font-medium uppercase tracking-widest">
              {props.badge}
            </span>
          )}
          {props.title && <h1 className="mt-4 text-4xl font-bold tracking-tight md:text-5xl">{props.title}</h1>}
          {props.subtitle && <p className="mx-auto mt-4 max-w-2xl text-lg opacity-90 md:mx-0">{props.subtitle}</p>}
          <div className="mt-8 flex flex-wrap justify-center gap-3 md:justify-start">
            {props.buttonLabel && safeUrl(props.buttonHref) && (
              <a href={safeUrl(props.buttonHref)} data-slot="buttonPrimary" className={cn('inline-block rounded-md bg-white px-6 py-3 font-medium text-slate-900 hover:bg-slate-100', ringIf('buttonPrimary'))}>
                {props.buttonLabel}
              </a>
            )}
            {props.secondaryButtonLabel && safeUrl(props.secondaryButtonHref) && (
              <a href={safeUrl(props.secondaryButtonHref)} data-slot="buttonSecondary" className={cn('inline-block rounded-md px-6 py-3 font-medium text-white ring-1 ring-white/40 hover:bg-white/10', ringIf('buttonSecondary'))}>
                {props.secondaryButtonLabel}
              </a>
            )}
          </div>
        </div>
        {safeUrl(props.imageUrl) && (
          /* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */
          <img src={safeUrl(props.imageUrl)} alt={props.title || ''} data-slot="image" className={cn('w-full rounded-3xl shadow-2xl md:aspect-[4/3] md:object-cover', ringIf('image'))} />
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
            {safeUrl(img.url) && (
              /* eslint-disable-next-line @next/next/no-img-element -- URLs externas coladas pelo tenant */
              <img src={safeUrl(img.url)} alt={img.caption || ''} className="h-48 w-full object-cover" />
            )}
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
      {safeFrameSrc(props.embedUrl) && (
        <div className="overflow-hidden rounded-lg border border-black/10">
          <iframe src={safeFrameSrc(props.embedUrl)} title="Mapa" className="h-80 w-full" loading="lazy" sandbox="allow-scripts allow-same-origin allow-popups" referrerPolicy="no-referrer" />
        </div>
      )}
    </section>
  )
}

// ---- catálogo ampliado (10) — re-tematizado pra var(--cms-primary) + tons neutros ----

function BannerStripBlock({ props, activeSlot }: { props: BannerStripProps; activeSlot?: string }) {
  if (!props.message) return null
  return (
    <div className="px-6 py-3 text-center text-sm" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <span data-slot="message" className={cn('font-medium', slotRing(activeSlot, 'message'))}>{props.message}</span>
      {props.buttonLabel && safeUrl(props.buttonHref) && (
        <a href={safeUrl(props.buttonHref)} data-slot="button" className={cn('ml-3 font-semibold underline underline-offset-2 hover:no-underline', slotRing(activeSlot, 'button'))}>
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

function ImageTextSplitBlock({ props, activeSlot }: { props: ImageTextSplitProps; activeSlot?: string }) {
  const reverse = props.reverse === true
  return (
    <section className="mx-auto max-w-6xl px-6 py-16">
      <div className={`grid items-center gap-12 md:grid-cols-2 ${reverse ? 'md:[direction:rtl]' : ''}`}>
        <div className={cn('md:[direction:ltr]', slotRing(activeSlot, 'content', false))} data-slot="content">
          {props.eyebrow && <div className="text-xs uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>{props.eyebrow}</div>}
          {props.title && <h2 className="mt-2 text-3xl font-bold md:text-4xl">{props.title}</h2>}
          {props.body && <p className="mt-4 whitespace-pre-line opacity-80">{props.body}</p>}
          {props.buttonLabel && safeUrl(props.buttonHref) && (
            <a href={safeUrl(props.buttonHref)} data-slot="button" className={cn('mt-6 inline-block rounded-md px-6 py-3 font-medium text-white', slotRing(activeSlot, 'button', false))} style={{ background: 'var(--cms-primary)' }}>
              {props.buttonLabel}
            </a>
          )}
        </div>
        {safeUrl(props.imageUrl) && (
          <div className="md:[direction:ltr]">
            {/* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */}
            <img src={safeUrl(props.imageUrl)} alt={props.title || ''} data-slot="image" className={cn('aspect-[4/3] w-full rounded-3xl object-cover shadow-xl', slotRing(activeSlot, 'image', false))} />
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
              {safeUrl(p.imageUrl) && (
                /* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */
                <img src={safeUrl(p.imageUrl)} alt={p.name} className="aspect-[4/3] w-full object-cover" />
              )}
              <div className="p-6 text-slate-900">
                {p.popular && <div className="mb-2 text-xs font-semibold uppercase tracking-widest" style={{ color: 'var(--cms-primary)' }}>Mais escolhido</div>}
                <h3 className="text-xl font-bold">{p.name}</h3>
                {p.description && <p className="mt-2 text-sm opacity-70">{p.description}</p>}
                {p.price && <div className="mt-4 text-3xl font-bold tabular-nums">{p.price}</div>}
                {p.buttonLabel && (
                  <a href={safeUrl(p.buttonHref) || '#'} className="mt-6 inline-block w-full rounded-xl py-2.5 text-center font-semibold text-white" style={{ background: 'var(--cms-primary)' }}>
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

function CtaBlock({ props, activeSlot }: { props: CtaProps; activeSlot?: string }) {
  return (
    <section className="px-6 py-16 text-center" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      <div className="mx-auto max-w-4xl">
        <div data-slot="content" className={cn(slotRing(activeSlot, 'content'))}>
          {props.title && <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{props.title}</h2>}
          {props.subtitle && <p className="mx-auto mt-4 max-w-2xl opacity-90">{props.subtitle}</p>}
        </div>
        {props.buttonLabel && safeUrl(props.buttonHref) && (
          <a href={safeUrl(props.buttonHref)} data-slot="button" className={cn('mt-8 inline-block rounded-md bg-white px-8 py-3 font-semibold text-slate-900 hover:bg-slate-100', slotRing(activeSlot, 'button'))}>
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
  meada_navbar: MeadaNavbar,
  meada_footer: MeadaFooter,
  niches_grid: NichesGrid,
} as const

/** Renderiza um bloco (com key = block.id). Compartilhado entre CmsRender (público) e o editor. */
/** opts.activeSlot: SÓ o editor passa, e só pro bloco cujo slot está selecionado — desenha o ring na
 * SUB-PARTE do macro (resolve o destaque sumido em full-bleed). Ausente no /p/ público → render idêntico. */
export function renderCmsBlock(b: CmsBlock, opts?: { activeSlot?: string }): React.ReactElement | null {
  switch (b.type) {
    case 'hero': return <HeroBlock key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'text': return <TextBlock key={b.id} props={b.props} />
    case 'services': return <ServicesBlock key={b.id} props={b.props} />
    case 'contact': return <ContactBlock key={b.id} props={b.props} />
    case 'gallery': return <GalleryBlock key={b.id} props={b.props} />
    case 'faq': return <FaqBlock key={b.id} props={b.props} />
    case 'testimonials': return <TestimonialsBlock key={b.id} props={b.props} />
    case 'map': return <MapBlock key={b.id} props={b.props} />
    case 'banner_strip': return <BannerStripBlock key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'stats': return <StatsBlock key={b.id} props={b.props} />
    case 'feature_grid': return <FeatureGridBlock key={b.id} props={b.props} />
    case 'image_text_split': return <ImageTextSplitBlock key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'steps': return <StepsBlock key={b.id} props={b.props} />
    case 'columns': return <ColumnsBlock key={b.id} props={b.props} />
    case 'packages': return <PackagesBlock key={b.id} props={b.props} />
    case 'marquee': return <MarqueeBlock key={b.id} props={b.props} />
    case 'quote': return <QuoteBlock key={b.id} props={b.props} />
    case 'cta': return <CtaBlock key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'meada_hero': return <MeadaHero key={b.id} props={b.props} />
    case 'meada_services': return <MeadaServices key={b.id} props={b.props} />
    case 'meada_portfolio': return <MeadaPortfolio key={b.id} props={b.props} />
    case 'meada_cta': return <MeadaCta key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'meada_navbar': return <MeadaNavbar key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'meada_footer': return <MeadaFooter key={b.id} props={b.props} activeSlot={opts?.activeSlot} />
    case 'niches_grid': return <NichesGrid key={b.id} props={b.props} />
    default: return null
  }
}

// ---- layout estrutural: linhas → colunas (grid de 12, responsivo) ----------

// As 12 strings LITERAIS md:col-span-N (anti-tree-shake do Tailwind 4 — nunca interpolar).
const COL_SPAN: Record<number, string> = {
  1: 'md:col-span-1', 2: 'md:col-span-2', 3: 'md:col-span-3', 4: 'md:col-span-4',
  5: 'md:col-span-5', 6: 'md:col-span-6', 7: 'md:col-span-7', 8: 'md:col-span-8',
  9: 'md:col-span-9', 10: 'md:col-span-10', 11: 'md:col-span-11', 12: 'md:col-span-12',
}
function colSpanClass(w: CmsColumnWidth): string {
  if (w === 'auto') return 'md:col-span-12'
  return COL_SPAN[Math.max(1, Math.min(12, w))] ?? 'md:col-span-12'
}

/** Hooks OPCIONAIS de interação do EDITOR. Ausentes no /p/ público → render IDÊNTICO, parity preservada.
 *  Quando presentes, cada bloco vira um alvo clicável com destaque de seleção (ring em volta), e clicar
 *  na seção FORA de um bloco seleciona a linha. */
export type RowInteractive = {
  selectedBlockId?: string | null
  selectedRow?: boolean
  containsSelectedBlock?: boolean
  // SLOT selecionado: id do bloco que contém o slot + id do slot ativo. Quando setado, o destaque vai
  // pra SUB-PARTE (via activeSlot no renderer) e o ring-do-bloco é suprimido.
  selectedSlotBlockId?: string | null
  selectedSlotId?: string | null
  onSelectBlock?: (rowId: string, colId: string, blockId: string) => void
  onSelectRow?: (rowId: string) => void
}

/** Renderiza UMA linha: <section> com fundo/padding + grid responsivo de colunas (12 → 1 no mobile).
 * Compartilhado entre o público (/p/) e o preview do editor — garante parity. `interactive` só é passado
 * pelo editor; no /p/ é undefined e o render fica igual ao de produção. */
export function RowSection({ row, interactive }: { row: CmsRow; interactive?: RowInteractive }) {
  const p = row.props ?? {}
  const bgStyle = p.bg === 'primary' ? { background: 'var(--cms-primary)', color: '#fff' }
    : p.bg === 'muted' ? { background: 'rgba(0,0,0,0.04)' } : undefined
  const padY = { none: '', sm: 'py-6', md: 'py-12', lg: 'py-20' }[p.paddingY ?? 'md']
  const gap = { sm: 'gap-4', md: 'gap-6', lg: 'gap-10' }[p.gap ?? 'md']
  const maxW = { narrow: 'max-w-3xl', wide: 'max-w-6xl', full: 'max-w-none' }[p.maxWidth ?? 'wide']
  const align = { start: 'md:items-start', center: 'md:items-center', stretch: 'md:items-stretch' }[p.align ?? 'stretch']
  const px = p.maxWidth === 'full' ? '' : 'px-6'

  // No editor: clicar na seção FORA de um bloco (alvo = a própria <section>) seleciona a LINHA. Destaques
  // em 2 pesos: linha selecionada → anel FORTE (ring-2 primary/60); linha que CONTÉM o bloco selecionado
  // → realce de CONTEXTO por fundo tonal + barra lateral (NÃO anel: o ring-inset some atrás do halo do
  // bloco; fundo+barra não disputam pixel com ele). border-l-2 transparent fixa reserva o espaço pra não
  // "pular" o conteúdo ao ativar. No /p/ público (sem interactive) a seção é estática.
  const sectionProps = interactive
    ? {
        onClick: (e: React.MouseEvent) => { if (e.target === e.currentTarget) interactive.onSelectRow?.(row.id) },
        className: cn(
          padY, 'relative cursor-pointer border-l-2 border-transparent transition-colors',
          interactive.selectedRow
            ? 'ring-2 ring-inset ring-primary/60'
            : interactive.containsSelectedBlock && 'border-primary/50 bg-primary/[0.04]',
        ),
      }
    : { className: cn(padY) }

  return (
    <section {...sectionProps} style={bgStyle}>
      <div className={cn('mx-auto grid grid-cols-1 md:grid-cols-12', px, gap, align, maxW)}>
        {(row.columns ?? []).map((col) => (
          <div key={col.id} className={colSpanClass(col.width)}>
            {(col.blocks ?? []).map((b) => {
              if (!interactive) return renderCmsBlock(b)
              // slot ativo NESTE bloco → destaque vai pra sub-parte (activeSlot) e o ring-do-bloco some.
              const slotActive = interactive.selectedSlotBlockId === b.id ? (interactive.selectedSlotId ?? undefined) : undefined
              return (
                // wrapper clicável. No EDITOR:
                //  - o CONTEÚDO recebe pointer-events:none → links/botões NÃO navegam e o clique
                //    sempre cai no wrapper (seleciona o bloco). (slot é selecionado pela árvore.)
                //  - o RING é um OVERLAY absoluto POR CIMA (z + ring-inset) → aparece mesmo sobre
                //    blocos full-bleed (meada_*), que antes engoliam o ring-offset atrás do fundo.
                <div
                  key={b.id}
                  onClick={(e) => { e.stopPropagation(); interactive.onSelectBlock?.(row.id, col.id, b.id) }}
                  className="group relative cursor-pointer"
                >
                  <div className="pointer-events-none">
                    {renderCmsBlock(b, { activeSlot: slotActive })}
                  </div>
                  {!slotActive && (
                    <div
                      aria-hidden
                      className={cn(
                        'pointer-events-none absolute inset-0 z-10 rounded-sm transition',
                        // hover via group-hover (o overlay é pointer-events-none; quem recebe o
                        // mouse é o wrapper .group).
                        interactive.selectedBlockId === b.id
                          ? 'ring-2 ring-inset ring-blue-500 bg-blue-500/[0.06]'
                          : 'ring-inset group-hover:ring-2 group-hover:ring-blue-400/40',
                      )}
                    />
                  )}
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </section>
  )
}

export function CmsRender({
  title,
  blocks,
  theme,
  nav,
  navBase,
}: {
  title: string
  blocks: CmsRow[]
  theme: CmsTheme | null
  nav: CmsNavItem[]
  navBase: string
}) {
  // Se a página já tem uma navbar PRÓPRIA como bloco (meada_navbar), NÃO desenha o nav
  // genérico de fallback — senão aparecem dois menus (o cru por cima da navbar da marca).
  // O nav genérico só serve a páginas sem navbar própria (tenant genérico multi-página).
  const hasOwnNavbar = blocks.some((row) =>
    (row.columns ?? []).some((col) => (col.blocks ?? []).some((b) => b.type === 'meada_navbar')),
  )
  return (
    <main className="cms-shell min-h-screen" style={cmsShellStyle(theme)} {...cmsShellLayoutAttrs(theme)}>
      {nav.length > 1 && !hasOwnNavbar && (
        <nav className="flex flex-wrap items-center justify-center gap-4 border-b border-black/10 px-6 py-4 text-sm">
          {nav.map((n) => (
            <a key={n.pageSlug} href={n.isHome ? `${navBase || '/'}` : `${navBase}/${n.pageSlug}`} className="hover:underline">
              {n.title || n.pageSlug}
            </a>
          ))}
        </nav>
      )}
      {blocks.map((row) => <RowSection key={row.id} row={row} />)}
      {blocks.length === 0 && (
        <div className="flex min-h-[50vh] items-center justify-center opacity-50">
          <p>{title || 'Página em construção.'}</p>
        </div>
      )}
    </main>
  )
}
