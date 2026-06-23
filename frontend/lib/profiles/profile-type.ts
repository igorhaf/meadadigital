/**
 * Catálogo MATERIALIZADO de perfis verticais (camada 7.0) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/ProfileType.java.
 *
 * Meada é um monolito que se apresenta como N produtos verticais ("perfis"). Cada perfil
 * parece um produto distinto para o cliente final. Os perfis são HARDCODED aqui e no enum
 * Java; o ProfileTypeParityTest (backend) garante que os dois nunca divergem — adicionar um
 * perfil = editar os 2 arquivos + a CHECK constraint da migration + rodar os testes.
 *
 * Campos: id (estável, persistido em companies.profile_id), productName (label do produto),
 * subdomain (sem domínio base), defaultPaletteId (ref. a lib/themes/palettes.ts).
 */
export const PROFILES = [
  { id: 'generic', productName: 'Meada', subdomain: 'meada', defaultPaletteId: 'meada-default' },
  { id: 'legal', productName: 'Legal', subdomain: 'juridico', defaultPaletteId: 'indigo' },
  { id: 'dental', productName: 'Dental', subdomain: 'dental', defaultPaletteId: 'celeste' },
  { id: 'sushi', productName: 'Sushi', subdomain: 'sushi', defaultPaletteId: 'tijolo' },
  { id: 'restaurant', productName: 'Restaurante', subdomain: 'mesa', defaultPaletteId: 'tijolo' },
  { id: 'salon', productName: 'Salão', subdomain: 'salao', defaultPaletteId: 'orquidea' },
  { id: 'pousada', productName: 'Pousada', subdomain: 'pousada', defaultPaletteId: 'oceano' },
  { id: 'academia', productName: 'Academia', subdomain: 'academia', defaultPaletteId: 'pinheiro' },
  { id: 'pet', productName: 'Pet', subdomain: 'pet', defaultPaletteId: 'coral' },
  { id: 'oficina', productName: 'Oficina', subdomain: 'oficina', defaultPaletteId: 'aco' },
  { id: 'nutri', productName: 'Nutri', subdomain: 'nutri', defaultPaletteId: 'salvia' },
  { id: 'barbearia', productName: 'Barbearia', subdomain: 'barbearia', defaultPaletteId: 'grafite' },
  { id: 'eventos', productName: 'Eventos', subdomain: 'eventos', defaultPaletteId: 'ambar' },
  { id: 'estetica', productName: 'Estética', subdomain: 'estetica', defaultPaletteId: 'rosa-po' },
  { id: 'comida', productName: 'Comida', subdomain: 'comida', defaultPaletteId: 'terracota' },
] as const

export type Profile = (typeof PROFILES)[number]
export type ProfileId = Profile['id']

/** Resolve um perfil pelo id estável (companies.profile_id). undefined se inválido. */
export function getProfile(id: string): Profile | undefined {
  return PROFILES.find((p) => p.id === id)
}

/** Resolve um perfil pelo subdomínio (ex.: "juridico" → legal). undefined se inválido. */
export function getProfileBySubdomain(sub: string): Profile | undefined {
  return PROFILES.find((p) => p.subdomain === sub)
}

/** Perfil genérico (fallback universal): login universal, comportamento atual. */
export const GENERIC_PROFILE: Profile = PROFILES[0]
