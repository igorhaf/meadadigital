/**
 * Catálogo HARDCODED de tipos de bloco do CMS (SM-M, page builder) — espelho 1:1 de
 * src/main/java/com/meada/cms/CmsBlockType.java.
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
  { id: 'reviews_carousel', label: 'Avaliações (carrossel)' },
  { id: 'video', label: 'Vídeo (YouTube/Vimeo)' },
  { id: 'rating_badge', label: 'Selo de avaliação' },
  { id: 'logo_strip', label: 'Faixa de logos' },
  { id: 'meada_hero', label: 'Meada · Hero' },
  { id: 'meada_services', label: 'Meada · Serviços' },
  { id: 'meada_portfolio', label: 'Meada · Portfólio' },
  { id: 'meada_cta', label: 'Meada · Chamada final' },
  { id: 'meada_navbar', label: 'Meada · Navbar' },
  { id: 'meada_footer', label: 'Meada · Rodapé' },
  { id: 'niches_grid', label: 'Meada · Grade de Nichos' },
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
export type PackagesProps = {
  eyebrow: string
  title: string
  subtitle: string
  items: PackageItem[]
}
export type MarqueeItem = { name: string }
export type MarqueeProps = { label: string; items: MarqueeItem[] }
export type QuoteProps = { text: string; author: string; role: string }
export type CtaProps = { title: string; subtitle: string; buttonLabel: string; buttonHref: string }

// ---- Onda 1 de blocos genéricos de site (prova social e mídia) ----
// rating é STRING '1'..'5' (select no editor); avatarUrl vazio → inicial colorida.
export type ReviewItem = {
  name: string
  text: string
  rating: string
  date: string
  avatarUrl: string
}
/** source 'google' desenha o selo "via Google" nos cards; 'manual' não. */
export type ReviewsCarouselProps = {
  title: string
  source: 'google' | 'manual'
  autoplay: boolean
  items: ReviewItem[]
}
/** url aceita link de YouTube (watch/shorts/youtu.be/embed) ou Vimeo — convertido em embed seguro. */
export type VideoProps = { title: string; url: string; caption: string }
export type RatingBadgeProps = { score: string; caption: string; href: string }
export type LogoItem = { name: string; imageUrl: string; href: string }
export type LogoStripProps = { label: string; items: LogoItem[] }

// ---- Blocos da marca Meada (preset meada-dark) ----
export type MeadaStat = { value: string; label: string }
export type MeadaTermLine = { kind: 'cmd' | 'check' | 'info' | 'done'; text: string }
/** Hero institucional Meada — fiel ao meada-page. O título é prefixo + trecho em gradiente +
 * sufixo. showcase: 'terminal' (animação projeto.sh), 'chat' (widget de assistente) ou 'plan'
 * (card de plano/preço no lado direito — usado nas páginas de nicho). */
export type MeadaHeroProps = {
  titlePrefix: string
  gradientText: string
  titleSuffix: string
  subtitle: string
  primaryLabel: string
  primaryHref: string
  secondaryLabel: string
  secondaryHref: string
  stats: MeadaStat[]
  showcase: 'terminal' | 'chat' | 'plan'
  terminalTitle: string
  terminalLines: MeadaTermLine[]
  terminalCaptionLeft: string
  terminalCaptionRight: string
  chatTitle: string
  chatMessage: string
  planBadge: string
  planName: string
  planDescription: string
  planPrice: string
  planButtonLabel: string
  planButtonHref: string
}

/** Serviço do bloco meada_services. icon = nome lucide (Code, Cloud, Heart, Smartphone, Layers,
 * BarChart3…); color = cor de destaque do card. */
export type MeadaServiceItem = {
  icon: string
  color: string
  title: string
  description: string
  linkLabel: string
  linkHref: string
}
export type MeadaServicesProps = { eyebrow: string; title: string; items: MeadaServiceItem[] }

/** Item do portfólio (curado — imagens por URL). category = badge; accentColor = cor; tags csv. */
export type MeadaPortfolioItem = {
  name: string
  category: string
  description: string
  imageUrl: string
  accentColor: string
  tags: string
  href: string
}
export type MeadaPortfolioProps = {
  eyebrow: string
  title: string
  linkLabel: string
  linkHref: string
  items: MeadaPortfolioItem[]
}

export type MeadaCtaProps = {
  titlePrefix: string
  gradientText: string
  subtitle: string
  primaryLabel: string
  primaryHref: string
  secondaryLabel: string
  secondaryHref: string
}

export type MeadaNavLink = { label: string; href: string }
export type MeadaNavbarProps = {
  brandName: string
  brandSuffix: string
  links: MeadaNavLink[]
  ctaLabel: string
  ctaHref: string
}
export type MeadaFooterColumn = { heading: string; links: MeadaNavLink[] }
export type MeadaFooterProps = {
  brandName: string
  brandSuffix: string
  tagline: string
  instagramUrl: string
  whatsappUrl: string
  columns: MeadaFooterColumn[]
  copyright: string
}

/**
 * Grade de nichos (produtos do Meada) — bloco AUTO-POPULADO: não tem lista de cards nas props.
 * Renderiza os nichos vindos do banco (/public/niches). mode 'featured' = só os destaques (home);
 * 'all' = todos na ordem (/produtos). Editar este bloco (eyebrow/title/mode) afeta a grade inteira.
 */
export type NichesGridProps = {
  eyebrow: string
  title: string
  mode: 'featured' | 'all'
}

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
  | { id: string; type: 'reviews_carousel'; props: ReviewsCarouselProps }
  | { id: string; type: 'video'; props: VideoProps }
  | { id: string; type: 'rating_badge'; props: RatingBadgeProps }
  | { id: string; type: 'logo_strip'; props: LogoStripProps }
  | { id: string; type: 'meada_hero'; props: MeadaHeroProps }
  | { id: string; type: 'meada_services'; props: MeadaServicesProps }
  | { id: string; type: 'meada_portfolio'; props: MeadaPortfolioProps }
  | { id: string; type: 'meada_cta'; props: MeadaCtaProps }
  | { id: string; type: 'meada_navbar'; props: MeadaNavbarProps }
  | { id: string; type: 'meada_footer'; props: MeadaFooterProps }
  | { id: string; type: 'niches_grid'; props: NichesGridProps }

/** Props default ao adicionar um bloco novo do tipo dado. */
export function defaultProps(type: CmsBlockTypeId): CmsBlock['props'] {
  switch (type) {
    case 'hero':
      return {
        title: '',
        subtitle: '',
        buttonLabel: '',
        buttonHref: '',
        badge: '',
        imageUrl: '',
        secondaryButtonLabel: '',
        secondaryButtonHref: '',
      }
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
      return {
        eyebrow: '',
        title: '',
        body: '',
        imageUrl: '',
        reverse: false,
        buttonLabel: '',
        buttonHref: '',
      }
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
    case 'reviews_carousel':
      return { title: 'O que dizem nossos clientes', source: 'google', autoplay: true, items: [] }
    case 'video':
      return { title: '', url: '', caption: '' }
    case 'rating_badge':
      return { score: '', caption: '', href: '' }
    case 'logo_strip':
      return { label: '', items: [] }
    case 'meada_hero':
      return {
        titlePrefix: 'Sites e Sistemas',
        gradientText: 'Sob Medida',
        titleSuffix: 'pra Crescer',
        subtitle:
          'Desenvolvimento personalizado do site institucional ao sistema completo. Código limpo, prazo claro e foco no que importa pro seu negócio.',
        primaryLabel: 'Comece Agora →',
        primaryHref: '/contato',
        secondaryLabel: 'Ver Produtos',
        secondaryHref: '/produtos',
        stats: [
          { value: '50+', label: 'Projetos' },
          { value: '20+', label: 'Tecnologias' },
          { value: '5+', label: 'Anos no mercado' },
        ],
        showcase: 'terminal',
        terminalTitle: 'meada — projeto.sh',
        terminalLines: [
          { kind: 'cmd', text: 'meada start --tipo=ecommerce' },
          { kind: 'info', text: 'Discovery e arquitetura definidos.' },
          { kind: 'check', text: 'Frontend Next.js + Tailwind' },
          { kind: 'check', text: 'Backend escalável + API REST' },
          { kind: 'check', text: 'Banco de dados + migrations' },
          { kind: 'check', text: 'Pagamentos integrados' },
          { kind: 'check', text: 'CI/CD + deploy em produção' },
          { kind: 'check', text: 'Painel admin para gestão' },
          { kind: 'done', text: 'Projeto entregue ✦ pronto pra escalar' },
        ],
        terminalCaptionLeft: 'do briefing ao ar em produção',
        terminalCaptionRight: '~ 2-6 sem',
        chatTitle: 'Assistente Meada',
        chatMessage: 'Olá! 👋 Sou o assistente da Meada Digital. Como posso te ajudar hoje?',
      }
    case 'meada_services':
      return {
        eyebrow: 'Capacidades',
        title: 'Tudo o Que Você Precisa para Crescer',
        items: [
          {
            icon: 'Code',
            color: '#60a5fa',
            title: 'Desenvolvimento Personalizado',
            description: 'Sites e sistemas feitos sob medida, do institucional ao mais complexo.',
            linkLabel: 'Saiba mais →',
            linkHref: '/servicos/desenvolvimento',
          },
          {
            icon: 'Cloud',
            color: '#a855f7',
            title: 'Infraestrutura em Nuvem',
            description: 'Deploy, CI/CD, monitoramento e escalabilidade sem dores de cabeça.',
            linkLabel: 'Saiba mais →',
            linkHref: '/servicos/nuvem',
          },
          {
            icon: 'Heart',
            color: '#ec4899',
            title: 'Manutenção & Suporte',
            description:
              'Acompanhamento contínuo, evolução de funcionalidades e correções com prazo previsível.',
            linkLabel: 'Saiba mais →',
            linkHref: '/contato',
          },
          {
            icon: 'Smartphone',
            color: '#22d3ee',
            title: 'Design Mobile First',
            description:
              'Experiências nativas e fluidas em qualquer dispositivo e tamanho de tela.',
            linkLabel: 'Saiba mais →',
            linkHref: '/servicos/mobile',
          },
          {
            icon: 'Layers',
            color: '#34d399',
            title: 'Design & UX',
            description: 'Interfaces bonitas e funcionais. Do wireframe ao Design System completo.',
            linkLabel: 'Saiba mais →',
            linkHref: '/servicos/design-ux',
          },
          {
            icon: 'BarChart3',
            color: '#f97316',
            title: 'APIs & Integrações',
            description:
              'Pagamentos, CRMs, ERPs e qualquer sistema conectado em uma arquitetura coesa.',
            linkLabel: 'Saiba mais →',
            linkHref: '/servicos/apis-integracoes',
          },
        ],
      }
    case 'meada_portfolio':
      return {
        eyebrow: 'Portfolio',
        title: 'Soluções Prontas para Usar',
        linkLabel: 'Ver todos os projetos →',
        linkHref: '/portfolio',
        items: [],
      }
    case 'meada_cta':
      return {
        titlePrefix: 'Pronto para',
        gradientText: 'Transformar seu Negócio?',
        subtitle:
          'Do site institucional ao sistema completo. Sem enrolação, com prazo claro e resultado.',
        primaryLabel: 'Agendar Consultoria',
        primaryHref: '/contato',
        secondaryLabel: 'Ver Produtos',
        secondaryHref: '/produtos',
      }
    case 'meada_navbar':
      return {
        brandName: 'Meada',
        brandSuffix: 'Digital',
        links: [
          { label: 'Serviços', href: '/servicos' },
          { label: 'Produtos', href: '/produtos' },
          { label: 'Sobre', href: '/sobre' },
          { label: 'Contato', href: '/contato' },
        ],
        ctaLabel: 'Pedir orçamento',
        ctaHref: '/contato',
      }
    case 'meada_footer':
      return {
        brandName: 'Meada',
        brandSuffix: 'Digital',
        tagline:
          'Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.',
        instagramUrl: 'https://instagram.com/meadadigital',
        whatsappUrl: 'https://wa.me/5581992612292',
        columns: [
          {
            heading: 'Serviços',
            links: [
              { label: 'Sites Profissionais', href: '/servicos' },
              { label: 'Sistemas sob Medida', href: '/servicos' },
              { label: 'Manutenção & Suporte', href: '/contato' },
            ],
          },
          {
            heading: 'Empresa',
            links: [
              { label: 'Sobre Nós', href: '/sobre' },
              { label: 'Produtos', href: '/produtos' },
              { label: 'Serviços', href: '/servicos' },
            ],
          },
          {
            heading: 'Contato',
            links: [
              { label: 'oi@meadadigital.com', href: 'mailto:oi@meadadigital.com' },
              { label: '(81) 99261-2292', href: 'https://wa.me/5581992612292' },
              { label: '@meadadigital', href: 'https://instagram.com/meadadigital' },
            ],
          },
        ],
        copyright: '© Meada Agência Digital. Todos os direitos reservados.',
      }
    case 'niches_grid':
      return {
        eyebrow: 'Produtos',
        title: 'Soluções por nicho',
        mode: 'featured',
      }
  }
}

// ============================================================================
// Árvore estrutural (page builder): página = linhas → colunas → blocos.
// A FOLHA (CmsBlock) NÃO muda; row/column são containers (não entram em
// CMS_BLOCK_TYPES nem no enum Java — não disparam o parity test).
// ============================================================================

/** Largura de uma coluna no grid de 12. 1..12 = span fixo; 'auto' = fallback (→ span 12). */
export type CmsColumnWidth = number | 'auto'

export type CmsRowProps = {
  bg?: 'none' | 'muted' | 'primary'
  paddingY?: 'none' | 'sm' | 'md' | 'lg'
  gap?: 'sm' | 'md' | 'lg'
  align?: 'start' | 'center' | 'stretch'
  maxWidth?: 'narrow' | 'wide' | 'full'
}

export type CmsColumn = { id: string; width: CmsColumnWidth; blocks: CmsBlock[] }
export type CmsRow = { id: string; props: CmsRowProps; columns: CmsColumn[] }
/** Página = árvore de linhas. O campo persistido continua se chamando `blocks`. */
export type CmsTree = CmsRow[]

let _seq = 0
function rand(prefix: string): string {
  _seq = (_seq + 1) % 1_000_000
  return (
    prefix +
    Date.now().toString(36) +
    '-' +
    _seq.toString(36) +
    Math.random().toString(36).slice(2, 6)
  )
}
export function newRowId(): string {
  return rand('r-')
}
export function newColId(): string {
  return rand('c-')
}
export function newBlockId(): string {
  return rand('b-')
}

export function defaultRowProps(): CmsRowProps {
  return { bg: 'none', paddingY: 'md', gap: 'md', align: 'stretch', maxWidth: 'wide' }
}
export function emptyColumn(width: CmsColumnWidth = 12): CmsColumn {
  return { id: newColId(), width, blocks: [] }
}
export function emptyRow(): CmsRow {
  return { id: newRowId(), props: defaultRowProps(), columns: [] }
}
/** Cria uma coluna já com um bloco do tipo dado (fluxo "arrastar componente → vira coluna"). */
export function columnWithBlock(type: CmsBlockTypeId, width: CmsColumnWidth = 12): CmsColumn {
  return {
    id: newColId(),
    width,
    blocks: [{ id: newBlockId(), type, props: defaultProps(type) } as CmsBlock],
  }
}
