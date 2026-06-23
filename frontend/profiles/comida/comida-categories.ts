/**
 * Categorias de cardápio do perfil comida (delivery iFood-style) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/comida/ComidaCategory.java.
 *
 * O ComidaCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de comida_menu_items.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const COMIDA_CATEGORIES = [
  { id: 'lanches', label: 'Lanches' },
  { id: 'pizzas', label: 'Pizzas' },
  { id: 'pratos', label: 'Pratos' },
  { id: 'porcoes', label: 'Porções' },
  { id: 'bebidas', label: 'Bebidas' },
  { id: 'sobremesas', label: 'Sobremesas' },
  { id: 'combos', label: 'Combos' },
] as const

export type ComidaCategory = (typeof COMIDA_CATEGORIES)[number]
export type ComidaCategoryId = ComidaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return COMIDA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
