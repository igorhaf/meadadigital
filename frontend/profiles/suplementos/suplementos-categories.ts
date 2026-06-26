/**
 * Categorias de produtos do perfil suplementos (loja de saúde / nutrição esportiva / varejo,
 * camada 8.24) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/suplementos/SuplementosCategory.java.
 *
 * Clone do lingerie-categories.ts adaptado às categorias de suplementos. O
 * SuplementosCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo). A CHECK constraint de
 * suplementos_products.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const SUPLEMENTOS_CATEGORIES = [
  { id: 'proteinas', label: 'Proteínas' },
  { id: 'aminoacidos', label: 'Aminoácidos' },
  { id: 'vitaminas', label: 'Vitaminas' },
  { id: 'pre_treino', label: 'Pré-treino' },
  { id: 'emagrecedores', label: 'Emagrecedores' },
  { id: 'acessorios', label: 'Acessórios' },
] as const

export type SuplementosCategory = (typeof SUPLEMENTOS_CATEGORIES)[number]
export type SuplementosCategoryId = SuplementosCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return SUPLEMENTOS_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
