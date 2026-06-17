/**
 * Categorias de cardápio do perfil sushi (camada 7.1) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/sushi/SushiCategory.java.
 *
 * O SushiCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de sushi_menu_items.category (migration 30) trava os mesmos ids no banco.
 * id = string estável (persistida); label = rótulo pt-BR exibido.
 */
export const SUSHI_CATEGORIES = [
  { id: 'entradas', label: 'Entradas' },
  { id: 'hot_rolls', label: 'Hot rolls' },
  { id: 'sashimi', label: 'Sashimi' },
  { id: 'combinados', label: 'Combinados' },
  { id: 'bebidas', label: 'Bebidas' },
  { id: 'sobremesas', label: 'Sobremesas' },
] as const

export type SushiCategory = (typeof SUSHI_CATEGORIES)[number]
export type SushiCategoryId = SushiCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return SUSHI_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
