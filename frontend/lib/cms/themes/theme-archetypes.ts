/**
 * ARQUÉTIPOS de tema — as ~10 "caras" reutilizáveis. Cada arquétipo define a FORMA (layout +
 * tipografia + radius/shadow + densidade + claro/escuro), sem cor. Os temas finais por nicho
 * (theme-catalog.ts) combinam um arquétipo com uma PALETA afim ao nicho.
 *
 * São 10 arquétipos cobrindo o espectro: do clean-corporativo ao bold-festivo, do editorial-luxo
 * ao técnico-mono. Cada nicho recebe os 10, instanciados com paletas próprias — então cada tenant
 * tem 10 temas, todos com identidade do seu segmento.
 */

import type { ThemeShape } from './theme-tokens'

export type Archetype = {
  /** id estável (entra no id do tema final: `{nicho}-{archetype}`) */
  id: string
  /** nome amigável mostrado no seletor */
  name: string
  /** clima/uso — uma linha */
  description: string
  shape: ThemeShape
  /** prefere fundo escuro? (a paleta do nicho fornece a versão dark/light correspondente) */
  prefersDark: boolean
}

export const ARCHETYPES: Archetype[] = [
  {
    id: 'clean',
    name: 'Clean',
    description: 'Claro, arejado e minimalista. Foco no conteúdo, bordas suaves, sem ruído.',
    prefersDark: false,
    shape: { font: 'sans', radius: 'md', shadow: 'soft', hero: 'centered', card: 'outline', density: 'spacious' },
  },
  {
    id: 'corporate',
    name: 'Corporativo',
    description: 'Sóbrio e confiável. Hero dividido, cards com leve elevação, ritmo equilibrado.',
    prefersDark: false,
    shape: { font: 'sans', radius: 'sm', shadow: 'md', hero: 'split', card: 'shadow', density: 'cozy' },
  },
  {
    id: 'editorial',
    name: 'Editorial',
    description: 'Serifada e elegante, títulos em caixa-alta com tracking. Ar de revista.',
    prefersDark: false,
    shape: { font: 'serif', radius: 'none', shadow: 'none', hero: 'minimal', card: 'flat', density: 'spacious', uppercaseHeadings: true },
  },
  {
    id: 'luxe',
    name: 'Luxo',
    description: 'Sofisticado e escuro, serifada, contrastes ricos. Premium e atemporal.',
    prefersDark: true,
    shape: { font: 'serif', radius: 'sm', shadow: 'soft', hero: 'overlay', card: 'outline', density: 'spacious', uppercaseHeadings: true },
  },
  {
    id: 'vibrant',
    name: 'Vibrante',
    description: 'Cores fortes e cantos generosos. Energia e modernidade, cards com sombra marcante.',
    prefersDark: false,
    shape: { font: 'display', radius: 'xl', shadow: 'bold', hero: 'split-reverse', card: 'elevated', density: 'cozy' },
  },
  {
    id: 'bold',
    name: 'Bold',
    description: 'Display impactante, hero centralizado de tela cheia, alto contraste.',
    prefersDark: true,
    shape: { font: 'display', radius: 'lg', shadow: 'bold', hero: 'centered', card: 'elevated', density: 'spacious', uppercaseHeadings: true },
  },
  {
    id: 'friendly',
    name: 'Acolhedor',
    description: 'Fonte arredondada, cantos macios, tom leve. Próximo e simpático.',
    prefersDark: false,
    shape: { font: 'rounded', radius: 'xl', shadow: 'soft', hero: 'split', card: 'shadow', density: 'cozy' },
  },
  {
    id: 'rustic',
    name: 'Rústico',
    description: 'Terroso e artesanal, serifada, pouca sombra, ar acolhedor e natural.',
    prefersDark: false,
    shape: { font: 'serif', radius: 'sm', shadow: 'none', hero: 'overlay', card: 'flat', density: 'cozy' },
  },
  {
    id: 'tech',
    name: 'Técnico',
    description: 'Monoespaçada, escuro, preciso. Ar de produto/tecnologia, cantos retos.',
    prefersDark: true,
    shape: { font: 'mono', radius: 'sm', shadow: 'soft', hero: 'minimal', card: 'outline', density: 'cozy' },
  },
  {
    id: 'festive',
    name: 'Festivo',
    description: 'Alegre e caloroso, cantos arredondados, sombras vivas. Celebração e movimento.',
    prefersDark: false,
    shape: { font: 'rounded', radius: 'xl', shadow: 'bold', hero: 'split-reverse', card: 'elevated', density: 'cozy' },
  },
]
