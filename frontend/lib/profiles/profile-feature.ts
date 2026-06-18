/**
 * Catálogo HARDCODED de features de plataforma (camada 9.0) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/ProfileFeature.java.
 *
 * O ROOT (super-admin) liga/desliga cada feature por nicho. Os perfis são hardcoded em
 * profile-type.ts; as features aqui. O ProfileFeatureParityTest (backend) garante que os keys
 * aqui e no enum Java nunca divergem — adicionar uma feature = editar os 2 arquivos + a CHECK
 * constraint de profile_features.feature_key (migration) + rodar a paridade.
 *
 * Default de toda feature = OFF (opt-in explícito do root). A primeira é 'cms' (página pessoal por
 * tenant); o CMS real vem na SM-M, atrás do gate hasFeature('cms').
 */
export const PROFILE_FEATURES = [
  { key: 'cms', label: 'Página pessoal (CMS)' },
] as const

export type ProfileFeature = (typeof PROFILE_FEATURES)[number]
export type ProfileFeatureKey = ProfileFeature['key']

/** Rótulo pt-BR de uma feature (fallback: o próprio key se desconhecido). */
export function featureLabel(key: string): string {
  return PROFILE_FEATURES.find((f) => f.key === key)?.label ?? key
}
