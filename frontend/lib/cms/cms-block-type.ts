/**
 * Catálogo HARDCODED de tipos de bloco do CMS (SM-M, page builder) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/cms/CmsBlockType.java.
 *
 * O CmsBlockTypeParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * Cada bloco de uma página é { id, type, props }; type é um destes. As props variam por tipo
 * (ver CmsBlock abaixo). Adicionar um tipo = editar os 2 arquivos + o editor/render + a paridade.
 *
 * Catálogo ampliado (estilo alegria): além dos 8 originais (hero/text/services/contact/gallery/
 * faq/testimonials/map), blocos de conteúdo (stats/feature_grid/image_text_split/steps/columns),
 * de marketing (banner_strip/marquee/quote/cta) e de pacotes (packages).
 */
export const CMS_BLOCK_TYPES = [
  { id: 'hero', label: 'Destaque (Hero)' },
  { id: 'text', label: 'Texto' },
  { id: 'services', label: 'Serviços' },
  { id: 'contact', label: 'Contato' },
  { id: 'gallery', label: 'Galeria' },
  { id: 'faq', label: 'Perguntas (FAQ)' },
  { id: 'testimonials', label: 'Depoimentos' },
  { id: 'map', label: 'Mapa' },
  { id: 'banner_strip', label: 'Faixa de aviso' },
  { id: 'stats', label: 'Números (estatísticas)' },
  { id: 'feature_grid', label: 'Diferenciais' },
  { id: 'image_text_split', label: 'Imagem + texto' },
  { id: 'steps', label: 'Passos' },
  { id: 'columns', label: 'Colunas' },
  { id: 'packages', label: 'Pacotes' },
  { id: 'marquee', label: 'Faixa de marcas' },
  { id: 'quote', label: 'Citação' },
  { id: 'cta', label: 'Chamada final (CTA)' },
] as const

export type CmsBlockTypeId = (typeof CMS_BLOCK_TYPES)[number]['id']

/** Rótulo pt-BR de um tipo de bloco (fallback: o próprio id). */
export function blockTypeLabel(id: string): string {
  return CMS_BLOCK_TYPES.find((t) => t.id === id)?.label ?? id
}

// ---- Props por tipo (shape do que vai em block.props) -----------------------

// Hero+ : os 4 últimos campos são opcionais (retrocompatível com heros antigos).
export type HeroProps = {
  title: string
  subtitle: string
  buttonLabel: string
  buttonHref: string
  badge?: string
  imageUrl?: string
  secondaryButtonLabel?: string
  secondaryButtonHref?: string
}
export type TextProps = { body: string } // markdown livre
export type ServiceItem = { name: string; description: string; price: string }
export type ServicesProps = { title: string; items: ServiceItem[] }
export type ContactProps = { phone: string; whatsapp: string; address: string; hours: string }
export type GalleryImage = { url: string; caption: string }
export type GalleryProps = { title: string; images: GalleryImage[] }
export type FaqItem = { question: string; answer: string }
export type FaqProps = { title: string; items: FaqItem[] }
export type Testimonial = { name: string; text: string; rating: string }
export type TestimonialsProps = { title: string; items: Testimonial[] }
export type MapProps = { title: string; address: string; embedUrl: string }

// ---- Catálogo ampliado ----
export type BannerStripProps = { message: string; buttonLabel: string; buttonHref: string }
export type StatItem = { value: string; label: string }
export type StatsProps = { items: StatItem[] }
export type FeatureItem = { icon: string; title: string; description: string }
export type FeatureGridProps = { eyebrow: string; title: string; items: FeatureItem[] }
export type ImageTextSplitProps = {
  eyebrow: string
  title: string
  body: string
  imageUrl: string
  reverse: boolean
  buttonLabel: string
  buttonHref: string
}
export type StepItem = { number: string; title: string; description: string }
export type StepsProps = { eyebrow: string; title: string; items: StepItem[] }
export type ColumnItem = { icon: string; title: string; body: string }
export type ColumnsProps = { eyebrow: string; title: string; items: ColumnItem[] }
export type PackageItem = {
  name: string
  description: string
  price: string
  imageUrl: string
  popular: boolean
  buttonLabel: string
  buttonHref: string
}
export type PackagesProps = { eyebrow: string; title: string; subtitle: string; items: PackageItem[] }
export type MarqueeItem = { name: string }
export type MarqueeProps = { label: string; items: MarqueeItem[] }
export type QuoteProps = { text: string; author: string; role: string }
export type CtaProps = { title: string; subtitle: string; buttonLabel: string; buttonHref: string }

export type CmsBlock =
  | { id: string; type: 'hero'; props: HeroProps }
  | { id: string; type: 'text'; props: TextProps }
  | { id: string; type: 'services'; props: ServicesProps }
  | { id: string; type: 'contact'; props: ContactProps }
  | { id: string; type: 'gallery'; props: GalleryProps }
  | { id: string; type: 'faq'; props: FaqProps }
  | { id: string; type: 'testimonials'; props: TestimonialsProps }
  | { id: string; type: 'map'; props: MapProps }
  | { id: string; type: 'banner_strip'; props: BannerStripProps }
  | { id: string; type: 'stats'; props: StatsProps }
  | { id: string; type: 'feature_grid'; props: FeatureGridProps }
  | { id: string; type: 'image_text_split'; props: ImageTextSplitProps }
  | { id: string; type: 'steps'; props: StepsProps }
  | { id: string; type: 'columns'; props: ColumnsProps }
  | { id: string; type: 'packages'; props: PackagesProps }
  | { id: string; type: 'marquee'; props: MarqueeProps }
  | { id: string; type: 'quote'; props: QuoteProps }
  | { id: string; type: 'cta'; props: CtaProps }

/** Props default ao adicionar um bloco novo do tipo dado. */
export function defaultProps(type: CmsBlockTypeId): CmsBlock['props'] {
  switch (type) {
    case 'hero':
      return { title: '', subtitle: '', buttonLabel: '', buttonHref: '', badge: '', imageUrl: '', secondaryButtonLabel: '', secondaryButtonHref: '' }
    case 'text':
      return { body: '' }
    case 'services':
      return { title: 'Serviços', items: [] }
    case 'contact':
      return { phone: '', whatsapp: '', address: '', hours: '' }
    case 'gallery':
      return { title: 'Galeria', images: [] }
    case 'faq':
      return { title: 'Perguntas frequentes', items: [] }
    case 'testimonials':
      return { title: 'Depoimentos', items: [] }
    case 'map':
      return { title: 'Onde estamos', address: '', embedUrl: '' }
    case 'banner_strip':
      return { message: '', buttonLabel: '', buttonHref: '' }
    case 'stats':
      return { items: [] }
    case 'feature_grid':
      return { eyebrow: '', title: 'Por que nos escolher', items: [] }
    case 'image_text_split':
      return { eyebrow: '', title: '', body: '', imageUrl: '', reverse: false, buttonLabel: '', buttonHref: '' }
    case 'steps':
      return { eyebrow: '', title: 'Como funciona', items: [] }
    case 'columns':
      return { eyebrow: '', title: '', items: [] }
    case 'packages':
      return { eyebrow: '', title: 'Nossos pacotes', subtitle: '', items: [] }
    case 'marquee':
      return { label: '', items: [] }
    case 'quote':
      return { text: '', author: '', role: '' }
    case 'cta':
      return { title: 'Pronto para começar?', subtitle: '', buttonLabel: '', buttonHref: '' }
  }
}
