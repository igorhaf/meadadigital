import { Sparkles, MousePointerClick, MousePointer, Image, Type, Megaphone, Tag, Share2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import type { CmsBlockTypeId } from '@/lib/cms/cms-block-type'

/**
 * SLOTS NOMEADOS (camada 9.x — sub-edição hierárquica dos macrocomponentes; HERO é o piloto).
 *
 * Um SLOT é uma PARTIÇÃO NOMEADA das props de um bloco — metadado PURO de UI, NÃO um nível de dado
 * persistido. `keys` referencia nomes de prop que JÁ EXISTEM no schema/JSONB do bloco; o slot não tem
 * props próprias nem id salvo. Por isso ZERO mudança em CmsService.normalizeBlocks, no contrato salvo
 * ou em migração — páginas antigas continuam válidas, e o /p/ público (RowSection sem `interactive`)
 * não muda em nada. O slot só existe no EDITOR: agrupa parte das props na árvore (sub-nó), filtra os
 * campos no painel direito, e ancora o destaque numa SUB-PARTE do bloco no preview.
 *
 * Invariantes (validáveis por `validateSlotKeys`): os `keys` de todos os slots de um tipo são (a)
 * disjuntos entre si e (b) subconjunto dos keys do blockSchema(type).fields. Keys do schema NÃO
 * cobertos por slot algum continuam aparecendo no modo BLOCO (pai) — fallback natural. Um tipo SEM
 * entrada aqui (os outros 23) se comporta como hoje: bloco é folha, sem chevron, sem sub-nós.
 *
 * Espalhar pra outro macro = adicionar UMA entrada em BLOCK_SLOTS (árvore + seleção + painel filtrado
 * vêm de graça). O DESTAQUE-fino no preview é retrofit por-renderer (o renderer do macro precisa anotar
 * suas sub-partes com data-slot/activeSlot) — opcional; sem ele, o slot ainda dá seleção/painel, só cai
 * no ring-do-bloco. O HERO é o piloto desse retrofit.
 */

export type SlotDef = {
  id: string        // estável por tipo (vira selection.slotId)
  label: string     // rótulo na árvore e no header do painel
  icon: LucideIcon  // ícone monocromático na árvore (mesmo vocabulário do BLOCK_ICONS)
  keys: string[]    // QUAIS props do bloco este slot agrupa (subconjunto dos keys do schema)
}

/** Slots por TIPO de bloco. Só o HERO no piloto; os demais ficam sem entrada (folha, como hoje). */
export const BLOCK_SLOTS: Partial<Record<CmsBlockTypeId, SlotDef[]>> = {
  hero: [
    { id: 'content', label: 'Conteúdo', icon: Sparkles, keys: ['badge', 'title', 'subtitle'] },
    { id: 'buttonPrimary', label: 'Botão primário', icon: MousePointerClick, keys: ['buttonLabel', 'buttonHref'] },
    { id: 'buttonSecondary', label: 'Botão secundário', icon: MousePointer, keys: ['secondaryButtonLabel', 'secondaryButtonHref'] },
    { id: 'image', label: 'Imagem lateral', icon: Image, keys: ['imageUrl'] },
  ],
  // Demais macros de PARTES FIXAS — entram só com a entrada aqui (árvore/seleção/painel filtrado de
  // graça). Os repeaters (navbar.links, footer.columns) ficam FORA dos slots nesta fase: as keys
  // listadas são só as partes fixas, e os fields não-cobertos seguem editáveis no modo bloco (pai).
  image_text_split: [
    { id: 'content', label: 'Conteúdo', icon: Type, keys: ['eyebrow', 'title', 'body'] },
    { id: 'image', label: 'Imagem', icon: Image, keys: ['imageUrl'] },
    { id: 'button', label: 'Botão', icon: MousePointerClick, keys: ['buttonLabel', 'buttonHref'] },
  ],
  cta: [
    { id: 'content', label: 'Conteúdo', icon: Type, keys: ['title', 'subtitle'] },
    { id: 'button', label: 'Botão', icon: MousePointerClick, keys: ['buttonLabel', 'buttonHref'] },
  ],
  banner_strip: [
    { id: 'message', label: 'Mensagem', icon: Megaphone, keys: ['message'] },
    { id: 'button', label: 'Botão', icon: MousePointerClick, keys: ['buttonLabel', 'buttonHref'] },
  ],
  meada_cta: [
    { id: 'content', label: 'Conteúdo', icon: Type, keys: ['titlePrefix', 'gradientText', 'subtitle'] },
    { id: 'buttonPrimary', label: 'Botão primário', icon: MousePointerClick, keys: ['primaryLabel', 'primaryHref'] },
    { id: 'buttonSecondary', label: 'Botão secundário', icon: MousePointer, keys: ['secondaryLabel', 'secondaryHref'] },
  ],
  meada_navbar: [
    { id: 'brand', label: 'Marca', icon: Tag, keys: ['brandName', 'brandSuffix'] },
    { id: 'cta', label: 'Botão CTA', icon: MousePointerClick, keys: ['ctaLabel', 'ctaHref'] },
  ],
  meada_footer: [
    { id: 'brand', label: 'Marca', icon: Tag, keys: ['brandName', 'brandSuffix', 'tagline'] },
    { id: 'social', label: 'Redes', icon: Share2, keys: ['instagramUrl', 'whatsappUrl'] },
  ],
}

/** Slots de um tipo (ou []). Helper único de leitura — árvore/painel/preview chamam por aqui. */
export function slotsForType(type: string): SlotDef[] {
  return BLOCK_SLOTS[type as CmsBlockTypeId] ?? []
}

/** Os fields de REPEATER de um schema (a 2ª família de sub-nós: services.items, gallery.images…).
 * Derivado direto do schema (type:'repeater') — não há mapa a manter em sync. `fields` = blockSchema.fields. */
export function repeaterFieldsOf<T extends { type: string }>(fields: T[]): T[] {
  return fields.filter((f) => f.type === 'repeater')
}

/**
 * Salvaguarda contra "prop fantasma": para um tipo, os keys de todos os slots devem ser disjuntos entre
 * si e estar TODOS contidos em `schemaKeys` (os keys do blockSchema). Devolve a lista de problemas
 * (vazia = ok). O editor a chama em dev (console.warn) — não há test runner no front, então essa é a
 * checagem automatizada possível no stack.
 */
export function validateSlotKeys(type: string, schemaKeys: string[]): string[] {
  const problems: string[] = []
  const seen = new Set<string>()
  const schema = new Set(schemaKeys)
  for (const slot of slotsForType(type)) {
    for (const k of slot.keys) {
      if (!schema.has(k)) problems.push(`${type}.${slot.id}: key "${k}" não existe no schema`)
      if (seen.has(k)) problems.push(`${type}.${slot.id}: key "${k}" duplicada em outro slot`)
      seen.add(k)
    }
  }
  return problems
}
