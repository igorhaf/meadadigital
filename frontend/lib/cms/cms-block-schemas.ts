/**
 * Schemas DECLARATIVOS dos blocos do CMS, pro editor visual (cms-block-canvas + field-renderer).
 * Cada bloco descreve seus campos editáveis; o FieldRenderer monta o form a partir disto.
 *
 * ⚠️ Os `key` (e o array-key de cada repeater) batem EXATAMENTE os nomes de prop em
 * `cms-block-type.ts` — o mesmo objeto editado aqui vira `block.props` e é lido pelo `cms-render.tsx`.
 * Um key errado quebra o render daquele bloco silenciosamente. Os DEFAULTS NÃO vivem aqui: usar
 * `defaultProps(type)` de `cms-block-type.ts` ao adicionar um bloco (fonte única de defaults).
 *
 * Tipos de campo: text, textarea, url, number, color, select, checkbox, repeater.
 */
import type { CmsBlockTypeId } from './cms-block-type'

export type FieldType = 'text' | 'textarea' | 'url' | 'number' | 'color' | 'select' | 'checkbox' | 'repeater'

export interface FieldDef {
  key: string
  label: string
  type: FieldType
  hint?: string
  placeholder?: string
  options?: { value: string; label: string }[] // select
  itemSchema?: FieldDef[] // repeater
  itemLabel?: string // rótulo do botão "adicionar item"
}

export interface BlockSchema {
  type: CmsBlockTypeId
  label: string
  description: string
  emoji: string
  fields: FieldDef[]
}

// ícones (emoji) sugeridos pra feature_grid / columns — o render aceita emoji direto.
const ICON_OPTIONS = [
  { value: '⭐', label: '⭐ Estrela' },
  { value: '💛', label: '💛 Coração' },
  { value: '✨', label: '✨ Brilho' },
  { value: '🛡️', label: '🛡️ Escudo' },
  { value: '✂️', label: '✂️ Tesoura' },
  { value: '💈', label: '💈 Barbearia' },
  { value: '📅', label: '📅 Agenda' },
  { value: '⏱️', label: '⏱️ Rapidez' },
  { value: '😊', label: '😊 Sorriso' },
  { value: '🎯', label: '🎯 Alvo' },
  { value: '💧', label: '💧 Gota' },
  { value: '🏆', label: '🏆 Troféu' },
]

export const blockSchemas: Record<CmsBlockTypeId, BlockSchema> = {
  hero: {
    type: 'hero', label: 'Destaque (Hero)', emoji: '✨',
    description: 'Banner principal com selo, título, subtítulo, botões e imagem.',
    fields: [
      { key: 'badge', label: 'Selo (etiqueta superior)', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'subtitle', label: 'Subtítulo', type: 'textarea' },
      { key: 'buttonLabel', label: 'Texto do botão', type: 'text' },
      { key: 'buttonHref', label: 'Link do botão', type: 'url' },
      { key: 'secondaryButtonLabel', label: 'Botão secundário (texto)', type: 'text' },
      { key: 'secondaryButtonHref', label: 'Botão secundário (link)', type: 'url' },
      { key: 'imageUrl', label: 'URL da imagem (lateral)', type: 'url' },
    ],
  },

  text: {
    type: 'text', label: 'Texto', emoji: '📝',
    description: 'Bloco de texto livre (parágrafos por linha em branco).',
    fields: [
      { key: 'body', label: 'Conteúdo', type: 'textarea' },
    ],
  },

  services: {
    type: 'services', label: 'Serviços', emoji: '🧰',
    description: 'Lista de serviços em cards (nome, descrição, preço).',
    fields: [
      { key: 'title', label: 'Título da seção', type: 'text' },
      { key: 'items', label: 'Serviços', type: 'repeater', itemLabel: 'serviço', itemSchema: [
        { key: 'name', label: 'Nome', type: 'text' },
        { key: 'description', label: 'Descrição', type: 'textarea' },
        { key: 'price', label: 'Preço', type: 'text' },
      ] },
    ],
  },

  contact: {
    type: 'contact', label: 'Contato', emoji: '📞',
    description: 'Telefone, WhatsApp, endereço e horário + botão de WhatsApp.',
    fields: [
      { key: 'phone', label: 'Telefone', type: 'text' },
      { key: 'whatsapp', label: 'WhatsApp (só números, com DDI)', type: 'text', placeholder: '5511999999999' },
      { key: 'address', label: 'Endereço', type: 'text' },
      { key: 'hours', label: 'Horário de funcionamento', type: 'text' },
    ],
  },

  gallery: {
    type: 'gallery', label: 'Galeria', emoji: '🖼️',
    description: 'Grade de fotos por URL com legenda.',
    fields: [
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'images', label: 'Imagens', type: 'repeater', itemLabel: 'imagem', itemSchema: [
        { key: 'url', label: 'URL da imagem', type: 'url' },
        { key: 'caption', label: 'Legenda', type: 'text' },
      ] },
    ],
  },

  faq: {
    type: 'faq', label: 'Perguntas (FAQ)', emoji: '❓',
    description: 'Lista expansível de perguntas e respostas.',
    fields: [
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'items', label: 'Perguntas', type: 'repeater', itemLabel: 'pergunta', itemSchema: [
        { key: 'question', label: 'Pergunta', type: 'text' },
        { key: 'answer', label: 'Resposta', type: 'textarea' },
      ] },
    ],
  },

  testimonials: {
    type: 'testimonials', label: 'Depoimentos', emoji: '💬',
    description: 'Cards com depoimentos de clientes.',
    fields: [
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'items', label: 'Depoimentos', type: 'repeater', itemLabel: 'depoimento', itemSchema: [
        { key: 'name', label: 'Nome', type: 'text' },
        { key: 'text', label: 'Texto', type: 'textarea' },
        // rating é STRING (ex.: "★★★★★") — text, NUNCA number.
        { key: 'rating', label: 'Avaliação (ex.: ★★★★★)', type: 'text' },
      ] },
    ],
  },

  map: {
    type: 'map', label: 'Mapa', emoji: '📍',
    description: 'Mapa do Google embarcado (iframe).',
    fields: [
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'address', label: 'Endereço', type: 'text' },
      { key: 'embedUrl', label: 'URL do iframe (Google Maps)', type: 'url' },
    ],
  },

  banner_strip: {
    type: 'banner_strip', label: 'Faixa de aviso', emoji: '📢',
    description: 'Faixa horizontal no topo, ideal pra chamada/promoção.',
    fields: [
      { key: 'message', label: 'Mensagem', type: 'text' },
      { key: 'buttonLabel', label: 'Texto do botão', type: 'text' },
      { key: 'buttonHref', label: 'Link', type: 'url' },
    ],
  },

  stats: {
    type: 'stats', label: 'Números (estatísticas)', emoji: '📊',
    description: 'Números em destaque sobre a cor primária.',
    fields: [
      { key: 'items', label: 'Estatísticas', type: 'repeater', itemLabel: 'número', itemSchema: [
        { key: 'value', label: 'Valor', type: 'text', hint: 'Ex.: 500+ ou 12 anos' },
        { key: 'label', label: 'Descrição', type: 'text' },
      ] },
    ],
  },

  feature_grid: {
    type: 'feature_grid', label: 'Diferenciais', emoji: '✅',
    description: 'Cards com ícone, título e descrição.',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'items', label: 'Diferenciais', type: 'repeater', itemLabel: 'diferencial', itemSchema: [
        { key: 'icon', label: 'Ícone', type: 'select', options: ICON_OPTIONS },
        { key: 'title', label: 'Título', type: 'text' },
        { key: 'description', label: 'Descrição', type: 'textarea' },
      ] },
    ],
  },

  image_text_split: {
    type: 'image_text_split', label: 'Imagem + texto', emoji: '🖼',
    description: 'Duas colunas: imagem de um lado, texto do outro.',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'body', label: 'Texto', type: 'textarea' },
      { key: 'imageUrl', label: 'URL da imagem', type: 'url' },
      { key: 'reverse', label: 'Inverter (imagem à esquerda)', type: 'checkbox' },
      { key: 'buttonLabel', label: 'Texto do botão', type: 'text' },
      { key: 'buttonHref', label: 'Link do botão', type: 'url' },
    ],
  },

  steps: {
    type: 'steps', label: 'Passos', emoji: '🪜',
    description: 'Lista numerada de etapas (3 colunas).',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'items', label: 'Passos', type: 'repeater', itemLabel: 'passo', itemSchema: [
        { key: 'number', label: 'Número', type: 'text', hint: 'Vazio = numera automático' },
        { key: 'title', label: 'Título', type: 'text' },
        { key: 'description', label: 'Descrição', type: 'textarea' },
      ] },
    ],
  },

  columns: {
    type: 'columns', label: 'Colunas', emoji: '🧱',
    description: 'Texto em colunas (2-4) com ícone.',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'items', label: 'Colunas', type: 'repeater', itemLabel: 'coluna', itemSchema: [
        { key: 'icon', label: 'Ícone', type: 'select', options: ICON_OPTIONS },
        { key: 'title', label: 'Título', type: 'text' },
        { key: 'body', label: 'Texto', type: 'textarea' },
      ] },
    ],
  },

  packages: {
    type: 'packages', label: 'Pacotes', emoji: '🎁',
    description: 'Cards de pacotes com imagem, preço, destaque e botão.',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'subtitle', label: 'Subtítulo', type: 'textarea' },
      { key: 'items', label: 'Pacotes', type: 'repeater', itemLabel: 'pacote', itemSchema: [
        { key: 'name', label: 'Nome', type: 'text' },
        { key: 'description', label: 'Descrição', type: 'textarea' },
        { key: 'price', label: 'Preço', type: 'text', hint: 'Ex.: R$ 60' },
        { key: 'imageUrl', label: 'URL da imagem', type: 'url' },
        { key: 'popular', label: 'Marcar como "mais escolhido"', type: 'checkbox' },
        { key: 'buttonLabel', label: 'Texto do botão', type: 'text' },
        { key: 'buttonHref', label: 'Link', type: 'url' },
      ] },
    ],
  },

  marquee: {
    type: 'marquee', label: 'Faixa de marcas', emoji: '🏷',
    description: 'Faixa horizontal de nomes/marcas.',
    fields: [
      { key: 'label', label: 'Etiqueta', type: 'text' },
      { key: 'items', label: 'Itens', type: 'repeater', itemLabel: 'item', itemSchema: [
        { key: 'name', label: 'Nome', type: 'text' },
      ] },
    ],
  },

  quote: {
    type: 'quote', label: 'Citação', emoji: '💭',
    description: 'Citação grande em destaque.',
    fields: [
      { key: 'text', label: 'Texto da citação', type: 'textarea' },
      { key: 'author', label: 'Autor', type: 'text' },
      { key: 'role', label: 'Papel/Empresa', type: 'text' },
    ],
  },

  cta: {
    type: 'cta', label: 'Chamada final (CTA)', emoji: '🚀',
    description: 'Bloco final com chamada para ação sobre a cor primária.',
    fields: [
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'subtitle', label: 'Subtítulo', type: 'textarea' },
      { key: 'buttonLabel', label: 'Texto do botão', type: 'text' },
      { key: 'buttonHref', label: 'Link do botão', type: 'url' },
    ],
  },

  meada_hero: {
    type: 'meada_hero', label: 'Meada · Hero', emoji: '💠',
    description: 'Hero institucional da marca Meada (preset meada-dark). Título com trecho em gradiente, botões, números e um showcase (terminal animado ou chat).',
    fields: [
      { key: 'titlePrefix', label: 'Título — início', type: 'text' },
      { key: 'gradientText', label: 'Título — trecho em gradiente', type: 'text' },
      { key: 'titleSuffix', label: 'Título — fim', type: 'text' },
      { key: 'subtitle', label: 'Subtítulo', type: 'textarea' },
      { key: 'primaryLabel', label: 'Botão principal — texto', type: 'text' },
      { key: 'primaryHref', label: 'Botão principal — link', type: 'url' },
      { key: 'secondaryLabel', label: 'Botão secundário — texto', type: 'text' },
      { key: 'secondaryHref', label: 'Botão secundário — link', type: 'url' },
      { key: 'stats', label: 'Números (3)', type: 'repeater', itemLabel: 'número', itemSchema: [
        { key: 'value', label: 'Valor', type: 'text', hint: 'Ex.: 50+' },
        { key: 'label', label: 'Descrição', type: 'text' },
      ] },
      { key: 'showcase', label: 'Showcase (lado direito)', type: 'select', options: [
        { value: 'terminal', label: 'Terminal animado (projeto.sh)' },
        { value: 'chat', label: 'Assistente / chat IA' },
      ] },
      { key: 'terminalTitle', label: 'Terminal — título', type: 'text' },
      { key: 'terminalLines', label: 'Terminal — linhas', type: 'repeater', itemLabel: 'linha', itemSchema: [
        { key: 'kind', label: 'Tipo', type: 'select', options: [
          { value: 'cmd', label: '$ comando' },
          { value: 'check', label: '✓ check' },
          { value: 'info', label: '› info' },
          { value: 'done', label: '✦ destaque' },
        ] },
        { key: 'text', label: 'Texto', type: 'text' },
      ] },
      { key: 'terminalCaptionLeft', label: 'Terminal — rodapé esquerda', type: 'text' },
      { key: 'terminalCaptionRight', label: 'Terminal — rodapé direita', type: 'text' },
      { key: 'chatTitle', label: 'Chat — título', type: 'text' },
      { key: 'chatMessage', label: 'Chat — 1ª mensagem', type: 'textarea' },
    ],
  },

  meada_services: {
    type: 'meada_services', label: 'Meada · Serviços', emoji: '🧩',
    description: 'Grade de serviços (cards com ícone colorido), fiel ao meada-page.',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'items', label: 'Serviços', type: 'repeater', itemLabel: 'serviço', itemSchema: [
        { key: 'icon', label: 'Ícone', type: 'select', options: [
          { value: 'Code', label: 'Code (desenvolvimento)' },
          { value: 'Cloud', label: 'Cloud (nuvem)' },
          { value: 'Heart', label: 'Heart (suporte)' },
          { value: 'Smartphone', label: 'Smartphone (mobile)' },
          { value: 'Layers', label: 'Layers (design)' },
          { value: 'BarChart3', label: 'BarChart (APIs)' },
          { value: 'Cpu', label: 'Cpu' }, { value: 'Globe', label: 'Globe' },
          { value: 'Rocket', label: 'Rocket' }, { value: 'Sparkles', label: 'Sparkles' },
          { value: 'Star', label: 'Star' }, { value: 'Target', label: 'Target' },
        ] },
        { key: 'color', label: 'Cor (hex)', type: 'text', placeholder: '#60a5fa' },
        { key: 'title', label: 'Título', type: 'text' },
        { key: 'description', label: 'Descrição', type: 'textarea' },
        { key: 'linkLabel', label: 'Link — texto', type: 'text' },
        { key: 'linkHref', label: 'Link — destino', type: 'url' },
      ] },
    ],
  },

  meada_portfolio: {
    type: 'meada_portfolio', label: 'Meada · Portfólio', emoji: '🗂️',
    description: 'Grade de projetos curados (imagem por URL, categoria, tags), fiel ao meada-page.',
    fields: [
      { key: 'eyebrow', label: 'Etiqueta superior', type: 'text' },
      { key: 'title', label: 'Título', type: 'text' },
      { key: 'linkLabel', label: 'Link "ver todos" — texto', type: 'text' },
      { key: 'linkHref', label: 'Link "ver todos" — destino', type: 'url' },
      { key: 'items', label: 'Projetos', type: 'repeater', itemLabel: 'projeto', itemSchema: [
        { key: 'name', label: 'Nome', type: 'text' },
        { key: 'category', label: 'Categoria (badge)', type: 'text', placeholder: 'Website' },
        { key: 'description', label: 'Descrição curta', type: 'textarea' },
        { key: 'imageUrl', label: 'URL da imagem (thumb)', type: 'url' },
        { key: 'accentColor', label: 'Cor de destaque (hex)', type: 'text', placeholder: '#3b82f6' },
        { key: 'tags', label: 'Tags (separadas por vírgula)', type: 'text', placeholder: 'Next.js, Tailwind, API' },
        { key: 'href', label: 'Link do projeto', type: 'url' },
      ] },
    ],
  },

  meada_cta: {
    type: 'meada_cta', label: 'Meada · Chamada final', emoji: '🚀',
    description: 'Bloco final com glow + título em gradiente e 2 botões, fiel ao meada-page.',
    fields: [
      { key: 'titlePrefix', label: 'Título — início', type: 'text' },
      { key: 'gradientText', label: 'Título — trecho em gradiente', type: 'text' },
      { key: 'subtitle', label: 'Subtítulo', type: 'textarea' },
      { key: 'primaryLabel', label: 'Botão principal — texto', type: 'text' },
      { key: 'primaryHref', label: 'Botão principal — link', type: 'url' },
      { key: 'secondaryLabel', label: 'Botão secundário — texto', type: 'text' },
      { key: 'secondaryHref', label: 'Botão secundário — link', type: 'url' },
    ],
  },
}

/** Schema de um tipo (ou undefined se desconhecido). */
export function blockSchema(type: string): BlockSchema | undefined {
  return blockSchemas[type as CmsBlockTypeId]
}

/** Lista ordenada de schemas pro catálogo (ordem de CMS_BLOCK_TYPES via Object.values). */
export function allBlockSchemas(): BlockSchema[] {
  return Object.values(blockSchemas)
}
