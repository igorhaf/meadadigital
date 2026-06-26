/**
 * Categorias de cardápio do perfil padaria (padaria & confeitaria) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/padaria/PadariaCategory.java.
 *
 * O PadariaCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de padaria_menu_items.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const PADARIA_CATEGORIES = [
  { id: 'paes', label: 'Pães' },
  { id: 'salgados', label: 'Salgados' },
  { id: 'doces_balcao', label: 'Doces de Balcão' },
  { id: 'bolos_encomenda', label: 'Bolos sob Encomenda' },
  { id: 'tortas', label: 'Tortas' },
  { id: 'bebidas', label: 'Bebidas' },
] as const

export type PadariaCategory = (typeof PADARIA_CATEGORIES)[number]
export type PadariaCategoryId = PadariaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return PADARIA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
