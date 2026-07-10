/**
 * TEMPLATES de página do CMS — estruturas prontas de blocos por PROPÓSITO. Enquanto o TEMA define a
 * CARA (cores/fonte/layout), o template define a ESTRUTURA: quais blocos, em que ordem, com conteúdo
 * de exemplo. Ao criar uma página, o tenant escolhe um template e já vem montado — não parte do zero.
 *
 * Catálogo amplo (não só "landing"): páginas universais (sobre, serviços, preços, equipe, promoção,
 * como-funciona, agenda, contato, galeria, FAQ, depoimentos, novidades...) + páginas com cara de
 * NICHO (cardápio, reservas, portfólio, catálogo, corpo clínico...) marcadas por afinidade.
 *
 * Cada template é `() => CmsRow[]` (ids frescos a cada chamada). Reusa defaultProps() por bloco e só
 * sobrescreve o conteúdo de exemplo. O seletor agrupa por categoria e destaca os afins ao nicho.
 */

import { defaultProps } from '@/lib/cms/cms-block-type'
import type { CmsBlock, CmsBlockTypeId, CmsRow, CmsRowProps } from '@/lib/cms/cms-block-type'

let _seq = 0
function uid(prefix: string): string {
  _seq = (_seq + 1) % 1_000_000
  return `${prefix}_${Date.now().toString(36)}_${_seq.toString(36)}`
}

function block(type: CmsBlockTypeId, props?: Record<string, unknown>): CmsBlock {
  return {
    id: uid('b'),
    type,
    props: { ...(defaultProps(type) as object), ...(props ?? {}) },
  } as CmsBlock
}

function row(b: CmsBlock, rowProps?: CmsRowProps): CmsRow {
  return {
    id: uid('r'),
    props: rowProps ?? {},
    columns: [{ id: uid('c'), width: 12, blocks: [b] }],
  }
}

/** Categoria do template (pra agrupar no seletor). */
export type TemplateCategory = 'principal' | 'institucional' | 'comercial' | 'conteudo' | 'nicho'

export type PageTemplate = {
  id: string
  name: string
  description: string
  icon: string
  category: TemplateCategory
  /** profile_ids para os quais este template é RECOMENDADO (vazio = universal, sem destaque). */
  affinity: string[]
  build: () => CmsRow[]
}

// ---- blocos de conteúdo reutilizáveis (helpers de exemplo) ------------------

const exHero = (title: string, subtitle: string, badge?: string) =>
  block('hero', {
    title,
    subtitle,
    badge: badge ?? '',
    buttonLabel: 'Fale conosco',
    buttonHref: '#contato',
    secondaryButtonLabel: '',
    secondaryButtonHref: '',
  })

const exFeatures = () =>
  block('feature_grid', {
    eyebrow: 'Por que nós',
    title: 'Nossos diferenciais',
    items: [
      { icon: '⭐', title: 'Qualidade', description: 'Descreva um diferencial do seu negócio.' },
      { icon: '⚡', title: 'Agilidade', description: 'Descreva outro diferencial importante.' },
      { icon: '🤝', title: 'Confiança', description: 'Mais um motivo para confiar em você.' },
    ],
  })

const exCta = (title: string, sub: string) =>
  block('cta', { title, subtitle: sub, buttonLabel: 'Falar no WhatsApp', buttonHref: '#contato' })

const exTestimonials = () =>
  block('testimonials', {
    title: 'O que dizem nossos clientes',
    items: [
      { name: 'Cliente satisfeito', text: 'Um depoimento real faz toda a diferença.', rating: 5 },
      { name: 'Outro cliente', text: 'Conte aqui a experiência de quem já contratou.', rating: 5 },
    ],
  })

// =============================================================================
// CATÁLOGO
// =============================================================================

export const PAGE_TEMPLATES: PageTemplate[] = [
  // ---- PRINCIPAL ------------------------------------------------------------
  {
    id: 'landing',
    name: 'Landing page',
    category: 'principal',
    affinity: [],
    description:
      'Página principal completa: destaque, diferenciais, serviços, depoimentos e chamada final.',
    icon: '🚀',
    build: () => [
      row(
        exHero(
          'Seu título de impacto aqui',
          'Uma frase curta que explica o que você faz e por que escolher o seu negócio.',
          'Bem-vindo',
        ),
      ),
      row(exFeatures(), { bg: 'muted' }),
      row(
        block('services', {
          title: 'O que oferecemos',
          items: [
            { name: 'Serviço 1', description: 'Breve descrição.', price: 'R$ 00' },
            { name: 'Serviço 2', description: 'Breve descrição.', price: 'R$ 00' },
            { name: 'Serviço 3', description: 'Breve descrição.', price: 'R$ 00' },
          ],
        }),
      ),
      row(exTestimonials(), { bg: 'muted' }),
      row(exCta('Pronto para começar?', 'Entre em contato agora e dê o primeiro passo.'), {
        bg: 'primary',
      }),
    ],
  },
  {
    id: 'landing-conversion',
    name: 'Landing de conversão',
    category: 'principal',
    affinity: [],
    description:
      'Focada em captar contato: faixa de oferta, prova social, passos e CTA forte. Ideal para campanhas.',
    icon: '🎯',
    build: () => [
      row(
        block('banner_strip', {
          message: 'Oferta por tempo limitado — condições especiais.',
          buttonLabel: 'Quero aproveitar',
          buttonHref: '#contato',
        }),
      ),
      row(
        exHero(
          'A solução que você procura',
          'Diga em uma frase o resultado que o cliente terá. Direto ao ponto.',
          'Novidade',
        ),
      ),
      row(
        block('stats', {
          items: [
            { value: '+500', label: 'Clientes' },
            { value: '4,9★', label: 'Avaliação' },
            { value: '24h', label: 'Resposta' },
          ],
        }),
        { bg: 'primary' },
      ),
      row(
        block('steps', {
          eyebrow: 'Simples assim',
          title: 'Como funciona',
          items: [
            {
              number: '1',
              title: 'Entre em contato',
              description: 'Fale com a gente pelo WhatsApp.',
            },
            {
              number: '2',
              title: 'Receba a proposta',
              description: 'Montamos a melhor solução pra você.',
            },
            { number: '3', title: 'Comece agora', description: 'É rápido e sem complicação.' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(exTestimonials()),
      row(exCta('Garanta o seu agora', 'Vagas/condições limitadas.'), { bg: 'primary' }),
    ],
  },

  // ---- INSTITUCIONAL --------------------------------------------------------
  {
    id: 'about',
    name: 'Sobre nós',
    category: 'institucional',
    affinity: [],
    description: 'História, valores e números. Texto + imagem + estatísticas.',
    icon: '📖',
    build: () => [
      row(
        exHero(
          'Nossa história',
          'Apresente seu negócio: como começou, o que move você e o que entrega ao cliente.',
          'Quem somos',
        ),
      ),
      row(
        block('image_text_split', {
          eyebrow: 'Nossa missão',
          title: 'O que nos guia',
          body: 'Escreva um parágrafo sobre os valores e a missão do seu negócio.',
          imageUrl: '',
        }),
      ),
      row(
        block('stats', {
          items: [
            { value: '+100', label: 'Clientes atendidos' },
            { value: '5 anos', label: 'De experiência' },
            { value: '4,9★', label: 'Avaliação média' },
          ],
        }),
        { bg: 'primary' },
      ),
      row(
        block('text', {
          body: 'Espaço livre para contar mais sobre a sua equipe, sua trajetória e o que torna o seu trabalho especial.',
        }),
      ),
    ],
  },
  {
    id: 'team',
    name: 'Equipe',
    category: 'institucional',
    affinity: [
      'salon',
      'barbearia',
      'estetica',
      'dental',
      'nutri',
      'dermatologia',
      'fotografia',
      'eventos',
      'casamento',
      'legal',
    ],
    description: 'Apresente os profissionais: cards com nome, função e foto.',
    icon: '👥',
    build: () => [
      row(exHero('Nossa equipe', 'Conheça os profissionais que cuidam de você.', 'Time')),
      row(
        block('columns', {
          eyebrow: 'Profissionais',
          title: 'Quem faz acontecer',
          items: [
            {
              icon: '👤',
              title: 'Nome do profissional',
              body: 'Função / especialidade. Uma linha sobre a experiência.',
            },
            {
              icon: '👤',
              title: 'Nome do profissional',
              body: 'Função / especialidade. Uma linha sobre a experiência.',
            },
            {
              icon: '👤',
              title: 'Nome do profissional',
              body: 'Função / especialidade. Uma linha sobre a experiência.',
            },
          ],
        }),
      ),
      row(
        block('gallery', {
          title: 'Nosso espaço',
          images: [
            { url: '', caption: '' },
            { url: '', caption: '' },
            { url: '', caption: '' },
          ],
        }),
        { bg: 'muted' },
      ),
    ],
  },
  {
    id: 'how-it-works',
    name: 'Como funciona',
    category: 'institucional',
    affinity: ['oficina', 'lavanderia', 'viagens', 'projetos', 'cursos', 'academia'],
    description: 'Explique seu processo passo a passo, com diferenciais e CTA.',
    icon: '🔧',
    build: () => [
      row(exHero('Como funciona', 'Entenda o passo a passo do nosso atendimento.', 'Processo')),
      row(
        block('steps', {
          eyebrow: 'Etapas',
          title: 'Do início ao fim',
          items: [
            { number: '1', title: 'Primeiro passo', description: 'Descreva a etapa inicial.' },
            { number: '2', title: 'Segundo passo', description: 'Descreva a etapa do meio.' },
            { number: '3', title: 'Terceiro passo', description: 'Descreva a etapa final.' },
          ],
        }),
      ),
      row(exFeatures(), { bg: 'muted' }),
      row(exCta('Ficou com alguma dúvida?', 'Fale com a gente.'), { bg: 'primary' }),
    ],
  },

  // ---- COMERCIAL ------------------------------------------------------------
  {
    id: 'services',
    name: 'Serviços',
    category: 'comercial',
    affinity: [],
    description: 'Lista de serviços/produtos com preços e dúvidas frequentes.',
    icon: '🗂️',
    build: () => [
      row(exHero('Nossos serviços', 'Conheça tudo o que oferecemos.')),
      row(
        block('services', {
          title: '',
          items: [
            { name: 'Serviço 1', description: 'Descrição.', price: 'R$ 00' },
            { name: 'Serviço 2', description: 'Descrição.', price: 'R$ 00' },
            { name: 'Serviço 3', description: 'Descrição.', price: 'R$ 00' },
            { name: 'Serviço 4', description: 'Descrição.', price: 'R$ 00' },
          ],
        }),
      ),
      row(
        block('faq', {
          title: 'Dúvidas frequentes',
          items: [
            { question: 'Como funciona o atendimento?', answer: 'Explique aqui.' },
            { question: 'Quais as formas de pagamento?', answer: 'Explique aqui.' },
          ],
        }),
        { bg: 'muted' },
      ),
    ],
  },
  {
    id: 'pricing',
    name: 'Preços e planos',
    category: 'comercial',
    affinity: ['academia', 'cursos', 'escola', 'estetica', 'pousada', 'suplementos'],
    description: 'Tabela de planos/pacotes com destaque do mais popular e FAQ.',
    icon: '💳',
    build: () => [
      row(exHero('Planos e preços', 'Escolha a opção ideal para você.')),
      row(
        block('packages', {
          eyebrow: 'Planos',
          title: 'Escolha o seu',
          subtitle: 'Sem fidelidade, cancele quando quiser.',
          items: [
            {
              name: 'Básico',
              description: 'Para começar.',
              price: 'R$ 00',
              popular: false,
              buttonLabel: 'Assinar',
              buttonHref: '#contato',
            },
            {
              name: 'Completo',
              description: 'O mais escolhido.',
              price: 'R$ 00',
              popular: true,
              buttonLabel: 'Assinar',
              buttonHref: '#contato',
            },
            {
              name: 'Premium',
              description: 'Tudo incluso.',
              price: 'R$ 00',
              popular: false,
              buttonLabel: 'Assinar',
              buttonHref: '#contato',
            },
          ],
        }),
      ),
      row(
        block('faq', {
          title: 'Perguntas sobre os planos',
          items: [
            { question: 'Posso trocar de plano depois?', answer: 'Sim, a qualquer momento.' },
            { question: 'Tem fidelidade?', answer: 'Explique aqui.' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(exCta('Comece hoje mesmo', 'Escolha seu plano e fale com a gente.'), { bg: 'primary' }),
    ],
  },
  {
    id: 'promo',
    name: 'Promoção / Oferta',
    category: 'comercial',
    affinity: [
      'comida',
      'pizzaria',
      'adega',
      'lingerie',
      'moda_infantil',
      'las',
      'suplementos',
      'floricultura',
    ],
    description: 'Página de campanha: faixa de oferta, produtos em destaque e CTA urgente.',
    icon: '🏷️',
    build: () => [
      row(
        block('banner_strip', {
          message: '🔥 Promoção da semana — aproveite!',
          buttonLabel: 'Ver ofertas',
          buttonHref: '#ofertas',
        }),
      ),
      row(exHero('Ofertas imperdíveis', 'Os melhores preços por tempo limitado.', 'Promoção')),
      row(
        block('packages', {
          eyebrow: 'Destaques',
          title: 'Em oferta',
          subtitle: '',
          items: [
            {
              name: 'Produto 1',
              description: 'Descrição.',
              price: 'R$ 00',
              popular: true,
              buttonLabel: 'Pedir',
              buttonHref: '#contato',
            },
            {
              name: 'Produto 2',
              description: 'Descrição.',
              price: 'R$ 00',
              popular: false,
              buttonLabel: 'Pedir',
              buttonHref: '#contato',
            },
            {
              name: 'Produto 3',
              description: 'Descrição.',
              price: 'R$ 00',
              popular: false,
              buttonLabel: 'Pedir',
              buttonHref: '#contato',
            },
          ],
        }),
      ),
      row(
        block('marquee', {
          label: 'Marcas e parceiros',
          items: [
            { name: 'Marca A' },
            { name: 'Marca B' },
            { name: 'Marca C' },
            { name: 'Marca D' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(exCta('Não perca!', 'Oferta válida enquanto durarem os estoques.'), { bg: 'primary' }),
    ],
  },
  {
    id: 'booking',
    name: 'Agendamento',
    category: 'comercial',
    affinity: [
      'salon',
      'barbearia',
      'estetica',
      'dental',
      'nutri',
      'dermatologia',
      'pet',
      'otica',
      'fotografia',
    ],
    description: 'Convide o cliente a marcar horário: passos, serviços e contato direto.',
    icon: '📅',
    build: () => [
      row(
        exHero(
          'Agende seu horário',
          'Marque seu atendimento de forma rápida pelo WhatsApp.',
          'Agendamento',
        ),
      ),
      row(
        block('steps', {
          eyebrow: 'Como agendar',
          title: 'É simples',
          items: [
            { number: '1', title: 'Escolha o serviço', description: 'Veja o que oferecemos.' },
            {
              number: '2',
              title: 'Fale conosco',
              description: 'Mande mensagem com o dia e horário.',
            },
            { number: '3', title: 'Pronto!', description: 'Confirmamos o seu agendamento.' },
          ],
        }),
      ),
      row(
        block('services', {
          title: 'Serviços disponíveis',
          items: [
            { name: 'Serviço 1', description: 'Duração e descrição.', price: 'R$ 00' },
            { name: 'Serviço 2', description: 'Duração e descrição.', price: 'R$ 00' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(
        block('contact', { phone: '', whatsapp: '', address: '', hours: 'Seg a Sáb, 9h às 19h' }),
      ),
    ],
  },

  // ---- CONTEÚDO -------------------------------------------------------------
  {
    id: 'gallery',
    name: 'Galeria / Portfólio',
    category: 'conteudo',
    affinity: ['fotografia', 'salon', 'estetica', 'atelie', 'eventos', 'casamento', 'projetos'],
    description: 'Mostre seu trabalho em imagens, com citação e CTA.',
    icon: '🖼️',
    build: () => [
      row(exHero('Nosso trabalho', 'Veja alguns dos nossos melhores resultados.')),
      row(
        block('gallery', {
          title: 'Galeria',
          images: [
            { url: '', caption: 'Legenda 1' },
            { url: '', caption: 'Legenda 2' },
            { url: '', caption: 'Legenda 3' },
            { url: '', caption: 'Legenda 4' },
            { url: '', caption: 'Legenda 5' },
            { url: '', caption: 'Legenda 6' },
          ],
        }),
      ),
      row(
        block('quote', {
          text: 'Um trabalho bem feito fala por si.',
          author: 'Sua marca',
          role: '',
        }),
        { bg: 'muted' },
      ),
      row(exCta('Gostou do que viu?', 'Vamos conversar sobre o seu projeto.'), { bg: 'primary' }),
    ],
  },
  {
    id: 'testimonials',
    name: 'Depoimentos',
    category: 'conteudo',
    affinity: [],
    description: 'Prova social: depoimentos de clientes, números e CTA.',
    icon: '💬',
    build: () => [
      row(exHero('O que dizem sobre nós', 'A opinião de quem já é nosso cliente.')),
      row(exTestimonials()),
      row(
        block('stats', {
          items: [
            { value: '+1000', label: 'Atendimentos' },
            { value: '98%', label: 'Recomendam' },
            { value: '4,9★', label: 'Nota média' },
          ],
        }),
        { bg: 'primary' },
      ),
      row(exCta('Faça parte você também', 'Fale com a gente.')),
    ],
  },
  {
    id: 'news',
    name: 'Novidades / Blog',
    category: 'conteudo',
    affinity: ['cursos', 'escola', 'nutri', 'dermatologia', 'viagens'],
    description: 'Espaço para conteúdo: artigos, dicas e novidades em destaque.',
    icon: '📰',
    build: () => [
      row(exHero('Novidades', 'Acompanhe nossas dicas e atualizações.', 'Blog')),
      row(
        block('columns', {
          eyebrow: 'Últimos posts',
          title: 'Confira',
          items: [
            { icon: '📝', title: 'Título do artigo 1', body: 'Resumo curto do conteúdo.' },
            { icon: '📝', title: 'Título do artigo 2', body: 'Resumo curto do conteúdo.' },
            { icon: '📝', title: 'Título do artigo 3', body: 'Resumo curto do conteúdo.' },
          ],
        }),
      ),
      row(
        block('text', {
          body: 'Use este espaço para um artigo completo, com vários parágrafos. O bloco de Texto aceita quebras de linha e formatação simples.',
        }),
        { bg: 'muted' },
      ),
    ],
  },
  {
    id: 'contact',
    name: 'Contato',
    category: 'conteudo',
    affinity: [],
    description: 'Como falar com você, horários e localização no mapa.',
    icon: '📞',
    build: () => [
      row(exHero('Fale conosco', 'Estamos prontos para te atender.')),
      row(
        block('contact', { phone: '', whatsapp: '', address: '', hours: 'Seg a Sex, 9h às 18h' }),
      ),
      row(block('map', { title: 'Onde estamos', address: '', embedUrl: '' }), { bg: 'muted' }),
    ],
  },
  {
    id: 'faq',
    name: 'Perguntas frequentes',
    category: 'conteudo',
    affinity: [],
    description: 'Tire as dúvidas dos clientes antes mesmo de eles perguntarem.',
    icon: '❓',
    build: () => [
      row(exHero('Perguntas frequentes', 'Reunimos as dúvidas mais comuns.')),
      row(
        block('faq', {
          title: '',
          items: [
            { question: 'Pergunta 1?', answer: 'Resposta clara e objetiva.' },
            { question: 'Pergunta 2?', answer: 'Resposta clara e objetiva.' },
            { question: 'Pergunta 3?', answer: 'Resposta clara e objetiva.' },
            { question: 'Pergunta 4?', answer: 'Resposta clara e objetiva.' },
          ],
        }),
      ),
      row(exCta('Ainda com dúvidas?', 'Fale com a gente.'), { bg: 'primary' }),
    ],
  },

  // ---- NICHO (estruturas com cara de segmento) ------------------------------
  {
    id: 'menu',
    name: 'Cardápio',
    category: 'nicho',
    affinity: ['comida', 'pizzaria', 'sushi', 'restaurant', 'padaria', 'adega'],
    description: 'Cardápio por categorias, com destaque do dia e chamada para pedir.',
    icon: '🍽️',
    build: () => [
      row(exHero('Nosso cardápio', 'Sabores preparados com carinho para você.', 'Cardápio')),
      row(
        block('banner_strip', {
          message: 'Peça pelo WhatsApp e receba em casa!',
          buttonLabel: 'Fazer pedido',
          buttonHref: '#contato',
        }),
      ),
      row(
        block('services', {
          title: 'Mais pedidos',
          items: [
            { name: 'Item 1', description: 'Ingredientes / descrição.', price: 'R$ 00' },
            { name: 'Item 2', description: 'Ingredientes / descrição.', price: 'R$ 00' },
            { name: 'Item 3', description: 'Ingredientes / descrição.', price: 'R$ 00' },
          ],
        }),
      ),
      row(
        block('services', {
          title: 'Bebidas',
          items: [
            { name: 'Bebida 1', description: '', price: 'R$ 00' },
            { name: 'Bebida 2', description: '', price: 'R$ 00' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(exCta('Bateu a fome?', 'Faça seu pedido agora.'), { bg: 'primary' }),
    ],
  },
  {
    id: 'rooms',
    name: 'Acomodações / Reservas',
    category: 'nicho',
    affinity: ['pousada', 'restaurant'],
    description: 'Mostre os quartos/mesas, comodidades e convide à reserva.',
    icon: '🏨',
    build: () => [
      row(exHero('Acomodações', 'Conforto e tranquilidade para a sua estadia.', 'Reservas')),
      row(
        block('packages', {
          eyebrow: 'Opções',
          title: 'Escolha o ideal',
          subtitle: '',
          items: [
            {
              name: 'Standard',
              description: 'Para 2 pessoas.',
              price: 'R$ 00/noite',
              popular: false,
              buttonLabel: 'Reservar',
              buttonHref: '#contato',
            },
            {
              name: 'Suíte',
              description: 'Mais espaço e conforto.',
              price: 'R$ 00/noite',
              popular: true,
              buttonLabel: 'Reservar',
              buttonHref: '#contato',
            },
            {
              name: 'Família',
              description: 'Para até 4 pessoas.',
              price: 'R$ 00/noite',
              popular: false,
              buttonLabel: 'Reservar',
              buttonHref: '#contato',
            },
          ],
        }),
      ),
      row(
        block('feature_grid', {
          eyebrow: 'Comodidades',
          title: 'O que oferecemos',
          items: [
            { icon: '📶', title: 'Wi-Fi grátis', description: '' },
            { icon: '🅿️', title: 'Estacionamento', description: '' },
            { icon: '☕', title: 'Café da manhã', description: '' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(
        block('gallery', {
          title: 'Conheça o espaço',
          images: [
            { url: '', caption: '' },
            { url: '', caption: '' },
            { url: '', caption: '' },
          ],
        }),
      ),
    ],
  },
  {
    id: 'catalog',
    name: 'Catálogo de produtos',
    category: 'nicho',
    affinity: [
      'lingerie',
      'moda_infantil',
      'las',
      'suplementos',
      'adega',
      'floricultura',
      'papelaria',
      'otica',
    ],
    description: 'Vitrine de produtos por categoria, com destaques e marcas.',
    icon: '🛍️',
    build: () => [
      row(exHero('Nossos produtos', 'Confira nossa seleção.', 'Catálogo')),
      row(
        block('packages', {
          eyebrow: 'Destaques',
          title: 'Mais vendidos',
          subtitle: '',
          items: [
            {
              name: 'Produto 1',
              description: 'Descrição.',
              price: 'R$ 00',
              popular: true,
              buttonLabel: 'Quero',
              buttonHref: '#contato',
            },
            {
              name: 'Produto 2',
              description: 'Descrição.',
              price: 'R$ 00',
              popular: false,
              buttonLabel: 'Quero',
              buttonHref: '#contato',
            },
            {
              name: 'Produto 3',
              description: 'Descrição.',
              price: 'R$ 00',
              popular: false,
              buttonLabel: 'Quero',
              buttonHref: '#contato',
            },
          ],
        }),
      ),
      row(
        block('gallery', {
          title: 'Coleção',
          images: [
            { url: '', caption: '' },
            { url: '', caption: '' },
            { url: '', caption: '' },
            { url: '', caption: '' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(
        block('marquee', {
          label: 'Marcas que trabalhamos',
          items: [{ name: 'Marca A' }, { name: 'Marca B' }, { name: 'Marca C' }],
        }),
      ),
    ],
  },
  {
    id: 'proposal',
    name: 'Orçamento / Proposta',
    category: 'nicho',
    affinity: [
      'eventos',
      'casamento',
      'projetos',
      'viagens',
      'atelie',
      'oficina',
      'fotografia',
      'concessionaria',
    ],
    description: 'Apresente pacotes e convide a pedir um orçamento personalizado.',
    icon: '📋',
    build: () => [
      row(
        exHero(
          'Vamos planejar juntos',
          'Conte o que você imagina e montamos uma proposta sob medida.',
          'Orçamento',
        ),
      ),
      row(
        block('steps', {
          eyebrow: 'Como funciona',
          title: 'Do briefing à entrega',
          items: [
            { number: '1', title: 'Briefing', description: 'Você nos conta o que precisa.' },
            { number: '2', title: 'Proposta', description: 'Montamos um orçamento detalhado.' },
            { number: '3', title: 'Execução', description: 'Cuidamos de tudo para você.' },
          ],
        }),
      ),
      row(
        block('packages', {
          eyebrow: 'Pacotes',
          title: 'Sugestões',
          subtitle: 'Tudo personalizável.',
          items: [
            {
              name: 'Essencial',
              description: 'O básico bem feito.',
              price: 'sob consulta',
              popular: false,
              buttonLabel: 'Pedir orçamento',
              buttonHref: '#contato',
            },
            {
              name: 'Completo',
              description: 'A opção mais procurada.',
              price: 'sob consulta',
              popular: true,
              buttonLabel: 'Pedir orçamento',
              buttonHref: '#contato',
            },
          ],
        }),
        { bg: 'muted' },
      ),
      row(
        block('gallery', {
          title: 'Trabalhos recentes',
          images: [
            { url: '', caption: '' },
            { url: '', caption: '' },
            { url: '', caption: '' },
          ],
        }),
      ),
      row(exCta('Vamos começar?', 'Peça seu orçamento sem compromisso.'), { bg: 'primary' }),
    ],
  },
  {
    id: 'clinic',
    name: 'Corpo clínico / Especialidades',
    category: 'nicho',
    affinity: ['dental', 'dermatologia', 'nutri', 'pet', 'otica'],
    description: 'Especialidades atendidas, equipe e orientações ao paciente.',
    icon: '🩺',
    build: () => [
      row(
        exHero('Cuidando da sua saúde', 'Atendimento humano e profissional para você.', 'Clínica'),
      ),
      row(
        block('feature_grid', {
          eyebrow: 'Atendemos',
          title: 'Especialidades',
          items: [
            { icon: '🦷', title: 'Especialidade 1', description: 'Breve descrição.' },
            { icon: '🔬', title: 'Especialidade 2', description: 'Breve descrição.' },
            { icon: '💊', title: 'Especialidade 3', description: 'Breve descrição.' },
          ],
        }),
      ),
      row(
        block('columns', {
          eyebrow: 'Equipe',
          title: 'Nossos profissionais',
          items: [
            { icon: '👤', title: 'Dr(a). Nome', body: 'Especialidade · registro profissional.' },
            { icon: '👤', title: 'Dr(a). Nome', body: 'Especialidade · registro profissional.' },
          ],
        }),
        { bg: 'muted' },
      ),
      row(
        block('faq', {
          title: 'Orientações ao paciente',
          items: [
            { question: 'Como agendar uma consulta?', answer: 'Explique aqui.' },
            { question: 'Atendem convênios?', answer: 'Explique aqui.' },
          ],
        }),
      ),
      row(block('contact', { phone: '', whatsapp: '', address: '', hours: '' }), { bg: 'muted' }),
    ],
  },

  // ---- EM BRANCO ------------------------------------------------------------
  {
    id: 'blank',
    name: 'Em branco',
    category: 'principal',
    affinity: [],
    description: 'Comece do zero e monte a página do seu jeito.',
    icon: '📄',
    build: () => [],
  },
]

export const TEMPLATE_CATEGORIES: { id: TemplateCategory; label: string }[] = [
  { id: 'principal', label: 'Principais' },
  { id: 'institucional', label: 'Institucional' },
  { id: 'comercial', label: 'Comercial' },
  { id: 'conteudo', label: 'Conteúdo' },
  { id: 'nicho', label: 'Para o seu segmento' },
]

/** Resolve um template pelo id (null se desconhecido). */
export function pageTemplateById(id: string): PageTemplate | null {
  return PAGE_TEMPLATES.find((t) => t.id === id) ?? null
}

/** Ordena os templates pondo os AFINS ao nicho primeiro (dentro de cada categoria). */
export function templatesForProfile(profileId: string): PageTemplate[] {
  return [...PAGE_TEMPLATES].sort((a, b) => {
    const aff = (t: PageTemplate) => (t.affinity.includes(profileId) ? 0 : 1)
    return aff(a) - aff(b)
  })
}
