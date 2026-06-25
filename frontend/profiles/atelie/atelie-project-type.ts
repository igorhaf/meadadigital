/**
 * Tipos de projeto do perfil atelie (camada 8.14) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/atelie/AtelieProjectType.java.
 *
 * O AtelieProjectTypeParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de atelie_proposals.project_type trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const ATELIE_PROJECT_TYPES = [
  { id: 'costura', label: 'Costura sob medida' },
  { id: 'arte', label: 'Arte' },
  { id: 'design', label: 'Design' },
] as const

export type AtelieProjectType = (typeof ATELIE_PROJECT_TYPES)[number]
export type AtelieProjectTypeId = AtelieProjectType['id']

/** Rótulo pt-BR de um tipo de projeto (fallback: o próprio id se desconhecido). */
export function typeLabel(id: string): string {
  return ATELIE_PROJECT_TYPES.find((t) => t.id === id)?.label ?? id
}
