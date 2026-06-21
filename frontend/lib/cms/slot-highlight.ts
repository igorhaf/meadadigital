import type { CSSProperties } from 'react'

/**
 * Helpers de DESTAQUE da sub-parte (slot) ativa de um macro. SEM dependências (evita ciclo de import
 * entre cms-render.tsx e os blocos meada_* que ele renderiza). `activeSlot` só é passado pelo editor e
 * só pro bloco cujo slot está selecionado; no /p/ público é undefined → vazio → nada aparece (parity).
 */

/** Classe Tailwind (ring) pra renderers que usam Tailwind. dark=true → ring branco (fundo escuro/primário);
 *  dark=false → ring na cor primária (fundo claro). */
export function slotRing(activeSlot: string | undefined, slotId: string, dark = true): string {
  if (activeSlot !== slotId) return ''
  return dark
    ? 'ring-2 ring-white ring-offset-2 ring-offset-[color:var(--cms-primary)] rounded-lg'
    : 'ring-2 ring-[color:var(--cms-primary)] ring-offset-2 rounded-lg'
}

/** Variante por STYLE inline pra renderers que não usam Tailwind (blocos meada_*). Usa `outline` (não
 *  box-shadow), que NÃO colide com o boxShadow inline desses blocos. Mesclar no style; vazio se inativo. */
export function slotOutlineStyle(activeSlot: string | undefined, slotId: string): CSSProperties {
  if (activeSlot !== slotId) return {}
  return { outline: '2px solid #fff', outlineOffset: '4px', borderRadius: '14px' }
}
