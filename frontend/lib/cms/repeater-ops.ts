import type { FieldDef } from '@/lib/cms/cms-block-schemas'

/**
 * Operações PURAS sobre os ITENS de um repeater (a 2ª família de sub-nós dos macros: services.items,
 * gallery.images, faq.items…). Trabalham sobre o VALOR do repeater (um array de objetos guardado em
 * `props[fieldKey]`) e devolvem o array novo — sem mutação. O editor pega o resultado e grava via a
 * MESMA updateBlockProps (`{ ...props, [fieldKey]: novoArray }`), então ZERO mudança no dado persistido
 * além do conteúdo do próprio repeater, que já existia. Item é um Record<string, unknown> (objeto livre
 * conforme o itemSchema do repeater).
 */

export type RepeaterItem = Record<string, unknown>

/** Lê o array do repeater de props (tolerante: não-array → []). */
export function getItems(props: Record<string, unknown>, fieldKey: string): RepeaterItem[] {
  const v = props[fieldKey]
  return Array.isArray(v) ? (v as RepeaterItem[]) : []
}

/** Item vazio conforme o itemSchema (checkbox→false, resto→''). Espelha o RepeaterField do field-renderer. */
export function emptyItem(itemSchema: FieldDef[] | undefined): RepeaterItem {
  const out: RepeaterItem = {}
  ;(itemSchema ?? []).forEach((f) => {
    out[f.key] = f.type === 'checkbox' ? false : ''
  })
  return out
}

export function addItem(items: RepeaterItem[], itemSchema: FieldDef[] | undefined): RepeaterItem[] {
  return [...items, emptyItem(itemSchema)]
}

export function removeItem(items: RepeaterItem[], index: number): RepeaterItem[] {
  if (index < 0 || index >= items.length) return items
  return items.filter((_, i) => i !== index)
}

export function moveItem(items: RepeaterItem[], index: number, dir: -1 | 1): RepeaterItem[] {
  const target = index + dir
  if (index < 0 || target < 0 || target >= items.length) return items
  const next = [...items]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

export function updateItem(
  items: RepeaterItem[],
  index: number,
  item: RepeaterItem,
): RepeaterItem[] {
  if (index < 0 || index >= items.length) return items
  return items.map((it, i) => (i === index ? item : it))
}

/** Rótulo curto de um item pra árvore (ex. "Serviço 2 · Corte"). Usa o 1º campo de texto preenchido
 * como dica; senão "<itemLabel> N". */
export function itemTitle(
  item: RepeaterItem,
  itemSchema: FieldDef[] | undefined,
  itemLabel: string,
  index: number,
): string {
  const base = `${itemLabel} ${index + 1}`
  const firstText = (itemSchema ?? []).find((f) => f.type === 'text' || f.type === 'textarea')
  const hint = firstText ? String(item[firstText.key] ?? '').trim() : ''
  return hint ? `${base} · ${hint.slice(0, 24)}` : base
}
