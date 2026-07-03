/**
 * Sistema de TEMAS do site (CMS) — tokens de design.
 *
 * Um "tema" é um conjunto coeso de tokens visuais (paleta + tipografia + forma + layout) que o
 * tenant escolhe pro seu site público. Os 26 blocos do CMS já herdam `--cms-primary`; o sistema de
 * temas AMPLIA esse conjunto de CSS vars (secundária, accent, fundo suave, radius, shadow, fonte) e
 * adiciona FLAGS DE LAYOUT (hero centralizado vs split, cards flat vs sombra, etc.) que os blocos
 * leem pra variar a forma sem mudar o conteúdo.
 *
 * Estrutura: um tema final = ARQUÉTIPO (a "cara": layout + forma + tom claro/escuro + fonte) ×
 * PALETA (as cores). Cada nicho recebe ~10 temas instanciando arquétipos com paletas afins —
 * ver theme-catalog.ts. Aqui ficam só os TOKENS e os tipos.
 */

/** Família tipográfica do tema (mapeada pra uma var de fonte já carregada no app, ou um stack). */
export type ThemeFont =
  | 'sans' // ui-sans moderno (Geist/system)
  | 'serif' // serifada editorial/luxo
  | 'rounded' // sans arredondada, amigável
  | 'mono' // monoespaçada técnica
  | 'display' // display marcante pra headings

/** Nível de canto arredondado. */
export type ThemeRadius = 'none' | 'sm' | 'md' | 'lg' | 'xl'

/** Nível de sombra dos cards/superfícies. */
export type ThemeShadow = 'none' | 'soft' | 'md' | 'bold'

/** Variação de layout do HERO. */
export type HeroLayout = 'centered' | 'split' | 'split-reverse' | 'minimal' | 'overlay'

/** Variação de layout dos CARDS (services/feature_grid/columns/packages). */
export type CardLayout = 'flat' | 'shadow' | 'outline' | 'elevated'

/** Densidade vertical (paddings das seções). */
export type ThemeDensity = 'compact' | 'cozy' | 'spacious'

/** Paleta de cores do tema (todas hex). */
export type ThemePalette = {
  primary: string
  secondary: string
  accent: string
  /** fundo geral do site */
  bg: string
  /** texto principal */
  fg: string
  /** fundo de superfícies suaves (seções alternadas, cards flat) */
  bgMuted: string
  /** borda sutil */
  border: string
  /** true = tema escuro (ajusta contrastes no render) */
  dark: boolean
}

/** Tokens de forma/tipografia/layout — a "cara" do tema, independente das cores. */
export type ThemeShape = {
  font: ThemeFont
  radius: ThemeRadius
  shadow: ThemeShadow
  hero: HeroLayout
  card: CardLayout
  density: ThemeDensity
  /** heading em caixa-alta com tracking (estilo editorial/luxo) */
  uppercaseHeadings?: boolean
}

/** Um tema final = identidade (id/nome/descrição) + paleta + forma. */
export type CmsThemeDef = {
  id: string
  name: string
  /** uma linha que descreve o clima do tema (mostrada no seletor) */
  description: string
  /** arquétipo de origem (pra agrupar/explicar no seletor) */
  archetype: string
  palette: ThemePalette
  shape: ThemeShape
}

// ---- mapeamento de tokens → valores CSS -------------------------------------

const FONT_STACK: Record<ThemeFont, string> = {
  sans: 'var(--font-geist-sans), ui-sans-serif, system-ui, sans-serif',
  serif: 'Georgia, "Times New Roman", "Noto Serif", serif',
  rounded: '"Nunito", "Quicksand", ui-rounded, "Segoe UI", system-ui, sans-serif',
  mono: 'var(--font-geist-mono), ui-monospace, "SFMono-Regular", monospace',
  display: '"Poppins", "Montserrat", var(--font-geist-sans), system-ui, sans-serif',
}

const RADIUS_PX: Record<ThemeRadius, string> = {
  none: '0px',
  sm: '4px',
  md: '10px',
  lg: '16px',
  xl: '24px',
}

const SHADOW_CSS: Record<ThemeShadow, string> = {
  none: 'none',
  soft: '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04)',
  md: '0 4px 12px rgba(0,0,0,0.10), 0 2px 4px rgba(0,0,0,0.06)',
  bold: '0 12px 32px rgba(0,0,0,0.18), 0 4px 8px rgba(0,0,0,0.08)',
}

const DENSITY_PADDING: Record<ThemeDensity, string> = {
  compact: '2.5rem',
  cozy: '4rem',
  spacious: '6rem',
}

/**
 * Resolve um tema em CSS vars + estilo base do shell. É o que o cmsShellStyle injeta no <main>.
 * Os blocos consomem essas vars (--cms-primary já existia; as demais são novas). As FLAGS de layout
 * (hero/card) são expostas como data-attributes via themeLayoutAttrs (abaixo), não como vars.
 */
export function themeToCssVars(t: CmsThemeDef): React.CSSProperties {
  const p = t.palette
  const s = t.shape
  return {
    ['--cms-primary' as string]: p.primary,
    ['--cms-secondary' as string]: p.secondary,
    ['--cms-accent' as string]: p.accent,
    ['--cms-bg' as string]: p.bg,
    ['--cms-fg' as string]: p.fg,
    ['--cms-bg-muted' as string]: p.bgMuted,
    ['--cms-border' as string]: p.border,
    ['--cms-radius' as string]: RADIUS_PX[s.radius],
    ['--cms-shadow' as string]: SHADOW_CSS[s.shadow],
    ['--cms-section-py' as string]: DENSITY_PADDING[s.density],
    ['--cms-heading-transform' as string]: s.uppercaseHeadings ? 'uppercase' : 'none',
    ['--cms-heading-tracking' as string]: s.uppercaseHeadings ? '0.08em' : 'normal',
    background: p.bg,
    color: p.fg,
    fontFamily: FONT_STACK[s.font],
  }
}

/** data-attributes de layout que o shell expõe; os blocos leem via `[data-hero=split]` etc. */
export function themeLayoutAttrs(t: CmsThemeDef): Record<string, string> {
  return {
    'data-hero': t.shape.hero,
    'data-card': t.shape.card,
    'data-theme-dark': t.palette.dark ? 'true' : 'false',
  }
}
