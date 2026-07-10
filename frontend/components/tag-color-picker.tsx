'use client'

import { TAG_COLORS, type TagColor } from '@/lib/supabase/tags'

/**
 * Classes Tailwind por cor de tag — chip colorido (bg suave + texto/borda saturados).
 * Mesma família de tons dos badges do projeto (bg-X-100 / text-X-700). 'slate' substitui
 * o cinza neutro. Usado tanto no chip exibido quanto no swatch selecionável do picker.
 */
export const TAG_COLOR_CLASSES: Record<TagColor, string> = {
  slate: 'bg-slate-100 text-slate-700',
  red: 'bg-red-100 text-red-700',
  orange: 'bg-orange-100 text-orange-700',
  amber: 'bg-amber-100 text-amber-700',
  green: 'bg-green-100 text-green-700',
  blue: 'bg-blue-100 text-blue-700',
  violet: 'bg-violet-100 text-violet-700',
  pink: 'bg-pink-100 text-pink-700',
}

/** Cor sólida do swatch (preenchimento do botão de seleção no picker). */
const SWATCH_BG: Record<TagColor, string> = {
  slate: 'bg-slate-500',
  red: 'bg-red-500',
  orange: 'bg-orange-500',
  amber: 'bg-amber-500',
  green: 'bg-green-500',
  blue: 'bg-blue-500',
  violet: 'bg-violet-500',
  pink: 'bg-pink-500',
}

/**
 * Chip colorido que exibe uma tag (nome + cor). Reusado na lista de tags, na coluna Tags
 * das conversas e na seção de tags do detalhe. onRemove opcional: quando presente,
 * mostra um "×" para desvincular (usado no detalhe da conversa).
 */
export function TagChip({
  name,
  color,
  onRemove,
}: {
  name: string
  color: TagColor
  onRemove?: () => void
}) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium ${TAG_COLOR_CLASSES[color]}`}
    >
      {name}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remover tag ${name}`}
          className="leading-none opacity-70 hover:opacity-100"
        >
          ×
        </button>
      )}
    </span>
  )
}

/**
 * Seletor visual de cor (camada 5.14 #22): 8 swatches clicáveis. O selecionado ganha um
 * anel. Controlado — value/onChange. Usado no modal de criar/editar tag (sem hex livre).
 */
export function TagColorPicker({
  value,
  onChange,
}: {
  value: TagColor
  onChange: (color: TagColor) => void
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {TAG_COLORS.map((color) => (
        <button
          key={color}
          type="button"
          onClick={() => onChange(color)}
          aria-label={`Cor ${color}`}
          aria-pressed={value === color}
          className={`size-7 rounded-full ${SWATCH_BG[color]} ${
            value === color ? 'ring-2 ring-foreground ring-offset-2' : ''
          }`}
        />
      ))}
    </div>
  )
}
