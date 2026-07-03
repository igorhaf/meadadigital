/**
 * CATÁLOGO de temas do site, por nicho. Cada um dos 34 nichos recebe os 10 ARQUÉTIPOS instanciados
 * com a PALETA-BASE afim ao seu segmento → 10 temas por nicho, com identidade do ramo.
 *
 * "Afinidade por nicho": além de gerar os 10, marcamos 2 arquétipos RECOMENDADOS por nicho (os que
 * mais combinam com o ramo) — o seletor destaca esses como "recomendado pra você".
 *
 * Como funciona a derivação de cor: cada nicho dá uma cor PRIMÁRIA + SECUNDÁRIA + ACCENT base. O
 * arquétipo decide se o tema é claro ou escuro (prefersDark) e os fundos/contrastes são derivados
 * automaticamente (deriveLight/deriveDark) — então a mesma paleta vira 10 climas diferentes.
 */

import { ARCHETYPES, type Archetype } from './theme-archetypes'
import type { CmsThemeDef, ThemePalette } from './theme-tokens'

/** Cores-semente de um nicho. */
type NichePalette = {
  primary: string
  secondary: string
  accent: string
  /** os 2 arquétipos que mais combinam com o nicho (ids de ARCHETYPES) */
  recommend: [string, string]
}

/**
 * Paleta-semente + recomendações por profile_id. Cores escolhidas pelo clima do segmento:
 * beleza→rosé/orquídea, comida→terracota/quente, clínico→teal/azul-clean, varejo→tons de marca, etc.
 */
const NICHE: Record<string, NichePalette> = {
  // ---- alimentação / delivery ----
  sushi: {
    primary: '#9e4438',
    secondary: '#1f2937',
    accent: '#caa24a',
    recommend: ['rustic', 'editorial'],
  },
  comida: {
    primary: '#b56a3d',
    secondary: '#2a2622',
    accent: '#e0a13a',
    recommend: ['friendly', 'vibrant'],
  },
  pizzaria: {
    primary: '#a13a4a',
    secondary: '#1f2937',
    accent: '#3a8a7d',
    recommend: ['vibrant', 'rustic'],
  },
  padaria: {
    primary: '#b85f2a',
    secondary: '#3a2e22',
    accent: '#caa24a',
    recommend: ['rustic', 'friendly'],
  },
  adega: {
    primary: '#6b3a6b',
    secondary: '#241a24',
    accent: '#c0a04a',
    recommend: ['luxe', 'editorial'],
  },
  restaurant: {
    primary: '#9e4438',
    secondary: '#2a2622',
    accent: '#3a7d8a',
    recommend: ['editorial', 'rustic'],
  },
  floricultura: {
    primary: '#b06a78',
    secondary: '#3a2e34',
    accent: '#6b8e6f',
    recommend: ['friendly', 'clean'],
  },
  suplementos: {
    primary: '#4f7d63',
    secondary: '#1f2a24',
    accent: '#c08a4a',
    recommend: ['tech', 'bold'],
  },

  // ---- beleza / estética / cuidado ----
  salon: {
    primary: '#8a4a85',
    secondary: '#2a2230',
    accent: '#b8a03a',
    recommend: ['vibrant', 'luxe'],
  },
  barbearia: {
    primary: '#3f4347',
    secondary: '#1a1c1e',
    accent: '#c98a3a',
    recommend: ['bold', 'tech'],
  },
  estetica: {
    primary: '#b06a78',
    secondary: '#2e2428',
    accent: '#4a8a7d',
    recommend: ['luxe', 'clean'],
  },
  fotografia: {
    primary: '#2b2e30',
    secondary: '#0f1112',
    accent: '#c79a4a',
    recommend: ['bold', 'editorial'],
  },

  // ---- saúde / clínico (clean, sóbrio) ----
  dental: {
    primary: '#3d7ab0',
    secondary: '#1f2a35',
    accent: '#4f7d63',
    recommend: ['clean', 'corporate'],
  },
  nutri: {
    primary: '#6b8e6f',
    secondary: '#222a23',
    accent: '#a86f4a',
    recommend: ['clean', 'friendly'],
  },
  dermatologia: {
    primary: '#2f6f6b',
    secondary: '#16201f',
    accent: '#c08a4a',
    recommend: ['clean', 'corporate'],
  },
  pet: {
    primary: '#c25f4f',
    secondary: '#2a201e',
    accent: '#4a8a8a',
    recommend: ['friendly', 'festive'],
  },
  otica: {
    primary: '#4c565e',
    secondary: '#1c2024',
    accent: '#c08a4a',
    recommend: ['corporate', 'clean'],
  },

  // ---- serviços / agenda ----
  academia: {
    primary: '#1f4d40',
    secondary: '#0f1f1a',
    accent: '#caa24a',
    recommend: ['bold', 'tech'],
  },
  pousada: {
    primary: '#2b5c8a',
    secondary: '#16242f',
    accent: '#d98a4a',
    recommend: ['luxe', 'editorial'],
  },
  oficina: {
    primary: '#4a6b85',
    secondary: '#1c242c',
    accent: '#c8854a',
    recommend: ['tech', 'corporate'],
  },
  lavanderia: {
    primary: '#3d7ab0',
    secondary: '#1c2a33',
    accent: '#4f7d63',
    recommend: ['clean', 'corporate'],
  },
  legal: {
    primary: '#3b4a8a',
    secondary: '#1a1f33',
    accent: '#c98a4a',
    recommend: ['corporate', 'editorial'],
  },

  // ---- celebração / projetos (proposta) ----
  eventos: {
    primary: '#d2a23a',
    secondary: '#2e2616',
    accent: '#5e4a8a',
    recommend: ['festive', 'luxe'],
  },
  casamento: {
    primary: '#cbab5e',
    secondary: '#2e2820',
    accent: '#5a4a7d',
    recommend: ['editorial', 'luxe'],
  },
  concessionaria: {
    primary: '#243b66',
    secondary: '#0f1626',
    accent: '#c79a4a',
    recommend: ['corporate', 'bold'],
  },
  viagens: {
    primary: '#2f5d3a',
    secondary: '#16241a',
    accent: '#d98a4a',
    recommend: ['vibrant', 'luxe'],
  },
  atelie: {
    primary: '#8a4a85',
    secondary: '#2a2230',
    accent: '#caa24a',
    recommend: ['editorial', 'rustic'],
  },

  // ---- educação ----
  escola: {
    primary: '#c69a2e',
    secondary: '#2a2412',
    accent: '#3d7ab0',
    recommend: ['friendly', 'festive'],
  },
  cursos: {
    primary: '#6e7038',
    secondary: '#22230f',
    accent: '#4a6b85',
    recommend: ['tech', 'corporate'],
  },

  // ---- varejo / moda ----
  lingerie: {
    primary: '#6b3a6b',
    secondary: '#241a24',
    accent: '#b06a78',
    recommend: ['luxe', 'vibrant'],
  },
  moda_infantil: {
    primary: '#c87a3a',
    secondary: '#2e2418',
    accent: '#3d7ab0',
    recommend: ['festive', 'friendly'],
  },
  las: {
    primary: '#b0503a',
    secondary: '#2a1c18',
    accent: '#6b8e6f',
    recommend: ['rustic', 'friendly'],
  },
  papelaria: {
    primary: '#7a6aa8',
    secondary: '#221f30',
    accent: '#caa24a',
    recommend: ['clean', 'editorial'],
  },

  // ---- genérico (fallback) ----
  generic: {
    primary: '#3a6b4a',
    secondary: '#1c2a20',
    accent: '#c98a3a',
    recommend: ['clean', 'corporate'],
  },
}

const FALLBACK: NichePalette = NICHE.generic

// ---- derivação de paleta clara/escura a partir das cores-semente ------------

/** Mistura uma cor hex com branco/preto por um fator (0..1). amount>0 clareia, <0 escurece. */
function mix(hex: string, amount: number): string {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  const t = amount < 0 ? 0 : 255
  const f = Math.abs(amount)
  const ch = (c: number) =>
    Math.round(c + (t - c) * f)
      .toString(16)
      .padStart(2, '0')
  return `#${ch(r)}${ch(g)}${ch(b)}`
}

function lightPalette(n: NichePalette): ThemePalette {
  return {
    primary: n.primary,
    secondary: n.secondary,
    accent: n.accent,
    bg: '#ffffff',
    fg: '#1a1a1a',
    bgMuted: mix(n.primary, 0.93), // tom muito claro da primária pra seções alternadas
    border: '#e5e7eb',
    dark: false,
  }
}

function darkPalette(n: NichePalette): ThemePalette {
  return {
    primary: mix(n.primary, 0.18), // primária um pouco mais clara pra contrastar no escuro
    secondary: n.secondary,
    accent: mix(n.accent, 0.1),
    bg: mix(n.secondary, -0.45), // fundo bem escuro derivado da secundária
    fg: '#ececec',
    bgMuted: mix(n.secondary, -0.3),
    border: 'rgba(255,255,255,0.10)',
    dark: true,
  }
}

/** Constrói o tema final pra (nicho, arquétipo). */
function buildTheme(profileId: string, arch: Archetype): CmsThemeDef {
  const n = NICHE[profileId] ?? FALLBACK
  const palette = arch.prefersDark ? darkPalette(n) : lightPalette(n)
  return {
    id: `${profileId}-${arch.id}`,
    name: arch.name,
    description: arch.description,
    archetype: arch.id,
    palette,
    shape: arch.shape,
  }
}

/** Os 10 temas de um nicho (um por arquétipo), na ordem do catálogo. */
export function themesForProfile(profileId: string): CmsThemeDef[] {
  return ARCHETYPES.map((a) => buildTheme(profileId, a))
}

/** Os arquétipos recomendados (ids) pra um nicho — destacados no seletor como "recomendado". */
export function recommendedArchetypes(profileId: string): string[] {
  return (NICHE[profileId] ?? FALLBACK).recommend
}

/** Resolve UM tema pelo id completo `{nicho}-{archetype}` (usado no render). null se desconhecido. */
export function themeById(themeId: string): CmsThemeDef | null {
  const dash = themeId.lastIndexOf('-')
  if (dash < 0) return null
  const profileId = themeId.slice(0, dash)
  const archId = themeId.slice(dash + 1)
  const arch = ARCHETYPES.find((a) => a.id === archId)
  if (!arch) return null
  return buildTheme(profileId, arch)
}
