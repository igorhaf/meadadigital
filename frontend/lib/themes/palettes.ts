/**
 * Catálogo versionado de paletas de tema (camada 5.0). Fonte única da verdade do
 * theming dinâmico: o banco persiste só o id (companies.palette_id / users.palette_id);
 * o ThemeProvider faz lookup aqui e injeta as CSS vars no :root.
 *
 * Contrato de cada paleta:
 *   - id: kebab-case único e ESTÁVEL (persistido no banco; nunca renomear um id em uso).
 *   - name: label legível pt-BR (evocativo).
 *   - primary: cor de marca principal.
 *   - primaryHover: ~10% mais escura que a primary (estados hover/active).
 *   - accent: cor harmônica complementar (destaques, badges).
 *   - surface: primary em ~10% de opacidade (#RRGGBB + alpha hex '1a') — fundos sutis.
 *   - textOnPrimary: '#ffffff' OU '#1a1a17', escolhido pelo contraste WCAG AA sobre a
 *     primary (texto/ícone que assenta SOBRE a cor primária).
 *
 * 'meada-default' (verde-musgo Meada) é a PRIMEIRA do array por contrato: é o fallback
 * universal e o default das colunas do banco. A ordem do array é a ordem de exibição no
 * PaletteSelect.
 */
export type Palette = {
  id: string
  name: string
  primary: string
  primaryHover: string
  accent: string
  surface: string
  textOnPrimary: '#ffffff' | '#1a1a17'
}

export const PALETTES: Palette[] = [
  // ---- Verdes (5, meada-default primeiro) ----------------------------------
  { id: 'meada-default', name: 'Meada (verde-musgo)', primary: '#3a6b4a', primaryHover: '#315b3f', accent: '#c98a3a', surface: '#3a6b4a1a', textOnPrimary: '#ffffff' },
  { id: 'floresta',      name: 'Floresta',            primary: '#2f5d3a', primaryHover: '#274e31', accent: '#b9743a', surface: '#2f5d3a1a', textOnPrimary: '#ffffff' },
  { id: 'eucalipto',     name: 'Eucalipto',           primary: '#4f7d63', primaryHover: '#436a54', accent: '#c08a4a', surface: '#4f7d631a', textOnPrimary: '#ffffff' },
  { id: 'pinheiro',      name: 'Pinheiro',            primary: '#1f4d40', primaryHover: '#194036', accent: '#caa24a', surface: '#1f4d401a', textOnPrimary: '#ffffff' },
  { id: 'salvia',        name: 'Sálvia',              primary: '#6b8e6f', primaryHover: '#5b7a5f', accent: '#a86f4a', surface: '#6b8e6f1a', textOnPrimary: '#ffffff' },

  // ---- Azuis (5) -----------------------------------------------------------
  { id: 'oceano',        name: 'Oceano',              primary: '#2b5c8a', primaryHover: '#244e75', accent: '#d98a4a', surface: '#2b5c8a1a', textOnPrimary: '#ffffff' },
  { id: 'meia-noite',    name: 'Meia-noite',          primary: '#243b66', primaryHover: '#1e3155', accent: '#c79a4a', surface: '#243b661a', textOnPrimary: '#ffffff' },
  { id: 'aco',           name: 'Aço',                 primary: '#4a6b85', primaryHover: '#3f5b71', accent: '#c8854a', surface: '#4a6b851a', textOnPrimary: '#ffffff' },
  { id: 'celeste',       name: 'Celeste',             primary: '#3d7ab0', primaryHover: '#346895', accent: '#b8693a', surface: '#3d7ab01a', textOnPrimary: '#ffffff' },
  { id: 'indigo',        name: 'Índigo',              primary: '#3b4a8a', primaryHover: '#323f75', accent: '#c98a4a', surface: '#3b4a8a1a', textOnPrimary: '#ffffff' },

  // ---- Vermelhos / coral (4) ----------------------------------------------
  { id: 'tijolo',        name: 'Tijolo',              primary: '#9e4438', primaryHover: '#853a30', accent: '#3a7d8a', surface: '#9e44381a', textOnPrimary: '#ffffff' },
  { id: 'carmim',        name: 'Carmim',              primary: '#a13a4a', primaryHover: '#88313f', accent: '#3a8a7d', surface: '#a13a4a1a', textOnPrimary: '#ffffff' },
  { id: 'coral',         name: 'Coral',               primary: '#c25f4f', primaryHover: '#a85044', accent: '#4a8a8a', surface: '#c25f4f1a', textOnPrimary: '#ffffff' },
  { id: 'ferrugem',      name: 'Ferrugem',            primary: '#b0503a', primaryHover: '#964430', accent: '#3a7d8a', surface: '#b0503a1a', textOnPrimary: '#ffffff' },

  // ---- Roxos (4) -----------------------------------------------------------
  { id: 'ameixa',        name: 'Ameixa',              primary: '#6b3a6b', primaryHover: '#5a315a', accent: '#c0a04a', surface: '#6b3a6b1a', textOnPrimary: '#ffffff' },
  { id: 'lavanda',       name: 'Lavanda',             primary: '#7a6aa8', primaryHover: '#685a90', accent: '#c0a04a', surface: '#7a6aa81a', textOnPrimary: '#ffffff' },
  { id: 'beringela',     name: 'Berinjela',           primary: '#4a3a5e', primaryHover: '#3f314f', accent: '#c8a24a', surface: '#4a3a5e1a', textOnPrimary: '#ffffff' },
  { id: 'orquidea',      name: 'Orquídea',            primary: '#8a4a85', primaryHover: '#754071', accent: '#b8a03a', surface: '#8a4a851a', textOnPrimary: '#ffffff' },

  // ---- Laranjas / terracota (3) -------------------------------------------
  { id: 'terracota',     name: 'Terracota',           primary: '#b56a3d', primaryHover: '#9b5a33', accent: '#3a7d8a', surface: '#b56a3d1a', textOnPrimary: '#ffffff' },
  { id: 'por-do-sol',    name: 'Pôr do sol',          primary: '#c87a3a', primaryHover: '#ad6831', accent: '#3a6b8a', surface: '#c87a3a1a', textOnPrimary: '#ffffff' },
  { id: 'abobora',       name: 'Abóbora',             primary: '#b85f2a', primaryHover: '#9e5124', accent: '#3a7d7d', surface: '#b85f2a1a', textOnPrimary: '#ffffff' },

  // ---- Amarelos / âmbar (3) — claros: texto escuro ------------------------
  { id: 'mostarda',      name: 'Mostarda',            primary: '#c69a2e', primaryHover: '#ab8327', accent: '#5e4a8a', surface: '#c69a2e1a', textOnPrimary: '#1a1a17' },
  { id: 'ambar',         name: 'Âmbar',               primary: '#d2a23a', primaryHover: '#b68a31', accent: '#5e4a8a', surface: '#d2a23a1a', textOnPrimary: '#1a1a17' },
  { id: 'trigo',         name: 'Trigo',               primary: '#cbab5e', primaryHover: '#b39450', accent: '#5a4a7d', surface: '#cbab5e1a', textOnPrimary: '#1a1a17' },

  // ---- Neutros (3) ---------------------------------------------------------
  { id: 'grafite',       name: 'Cinza-grafite',       primary: '#3f4347', primaryHover: '#34383b', accent: '#c98a3a', surface: '#3f43471a', textOnPrimary: '#ffffff' },
  { id: 'ardosia',       name: 'Ardósia',             primary: '#4c565e', primaryHover: '#414a51', accent: '#c08a4a', surface: '#4c565e1a', textOnPrimary: '#ffffff' },
  { id: 'carvao',        name: 'Carvão',              primary: '#2b2e30', primaryHover: '#222527', accent: '#c79a4a', surface: '#2b2e301a', textOnPrimary: '#ffffff' },

  // ---- Exóticos (3) --------------------------------------------------------
  { id: 'teal',          name: 'Teal',                primary: '#2f6f6b', primaryHover: '#285e5a', accent: '#c08a4a', surface: '#2f6f6b1a', textOnPrimary: '#ffffff' },
  { id: 'rosa-po',       name: 'Rosa-pó',             primary: '#b06a78', primaryHover: '#965a66', accent: '#4a8a7d', surface: '#b06a781a', textOnPrimary: '#ffffff' },
  { id: 'oliva',         name: 'Oliva',               primary: '#6e7038', primaryHover: '#5d5f30', accent: '#8a5a3a', surface: '#6e70381a', textOnPrimary: '#ffffff' },
]

/** Id da paleta padrão — fallback universal e default das colunas do banco. */
export const DEFAULT_PALETTE_ID = 'meada-default'

/**
 * Lookup por id com fallback para 'meada-default'. NUNCA retorna undefined — um id
 * desconhecido (paleta removida do catálogo mas ainda no banco) cai no default, evitando
 * tela sem tema. PALETTES[0] é meada-default por contrato.
 */
export function getPalette(id: string | null | undefined): Palette {
  return PALETTES.find((p) => p.id === id) ?? PALETTES[0]
}
