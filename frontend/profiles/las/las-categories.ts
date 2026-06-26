/**
 * Categorias de produtos do perfil las (loja de lãs / novelos / tricô-crochê — varejo, camada
 * 8.23) — espelho 1:1 de src/main/java/com/meada/whatsapp/profiles/las/LasCategory.java.
 *
 * Clone do lingerie-categories.ts adaptado às categorias de uma loja de lãs. O LasCategoryParityTest
 * (backend) garante que os ids aqui e no enum Java nunca divergem (o teste casa textualmente cada
 * objeto `{ id: '...' }` deste arquivo). A CHECK constraint de las_products.category trava os mesmos
 * ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const LAS_CATEGORIES = [
  { id: 'las', label: 'Lãs' },
  { id: 'linhas', label: 'Linhas' },
  { id: 'kits', label: 'Kits' },
  { id: 'agulhas', label: 'Agulhas' },
  { id: 'acessorios', label: 'Acessórios' },
  { id: 'pelucia', label: 'Pelúcia' },
] as const

export type LasCategory = (typeof LAS_CATEGORIES)[number]
export type LasCategoryId = LasCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return LAS_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
