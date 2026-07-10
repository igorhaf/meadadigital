'use client'

import { Select } from '@base-ui/react/select'

import { getPalette, PALETTES, type Palette } from '@/lib/themes/palettes'
import { cn } from '@/lib/utils'

/**
 * Seletor de paleta de tema (camada 5.0). NÃO é um <select> HTML (que só renderiza
 * texto): é o Select do base-ui com render custom, para cada opção mostrar o NOME +
 * uma fileira de 5 retângulos coloridos (primary, primaryHover, accent, surface,
 * textOnPrimary). Controlled: o pai detém o id e reage ao onChange.
 *
 * A acessibilidade (combobox role, navegação por teclado, foco gerenciado, typeahead)
 * vem do base-ui; aqui só compomos o visual e reforçamos aria-label no trigger.
 */
export type PaletteSelectProps = {
  value: string
  onChange: (id: string) => void
  disabled?: boolean
}

/** Fileira dos 5 retângulos de uma paleta (reusada no trigger e em cada item). */
function Swatches({ palette }: { palette: Palette }) {
  const colors: string[] = [
    palette.primary,
    palette.primaryHover,
    palette.accent,
    palette.surface,
    palette.textOnPrimary,
  ]
  return (
    <span className="flex shrink-0 items-center gap-0.5" aria-hidden="true">
      {colors.map((c, i) => (
        <span
          key={i}
          className="size-4 rounded-sm border border-black/10"
          style={{ backgroundColor: c }}
        />
      ))}
    </span>
  )
}

export function PaletteSelect({ value, onChange, disabled }: PaletteSelectProps) {
  const current = getPalette(value)

  return (
    <Select.Root value={value} onValueChange={(v) => onChange(v as string)} disabled={disabled}>
      <Select.Trigger
        aria-label={`Paleta de tema: ${current.name}`}
        className={cn(
          'flex h-9 w-full items-center justify-between gap-3 rounded-lg border border-border bg-background px-3 text-sm',
          'transition-colors outline-none hover:bg-muted focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50',
          'disabled:pointer-events-none disabled:opacity-50 aria-expanded:bg-muted',
        )}
      >
        <Select.Value>
          {(val: string) => {
            const p = getPalette(val)
            return (
              <span className="flex items-center gap-2">
                <span className="truncate">{p.name}</span>
              </span>
            )
          }}
        </Select.Value>
        <Swatches palette={current} />
      </Select.Trigger>

      <Select.Portal>
        <Select.Positioner sideOffset={4} className="z-50">
          <Select.Popup
            className={cn(
              'max-h-72 min-w-[var(--anchor-width)] overflow-y-auto rounded-lg border border-border bg-popover p-1 shadow-md',
              'outline-none',
            )}
          >
            <Select.List>
              {PALETTES.map((p) => (
                <Select.Item
                  key={p.id}
                  value={p.id}
                  className={cn(
                    'flex cursor-default items-center justify-between gap-3 rounded-md px-2 py-1.5 text-sm outline-none',
                    'data-[highlighted]:bg-muted data-[selected]:font-medium',
                  )}
                >
                  <Select.ItemText className="truncate">{p.name}</Select.ItemText>
                  <Swatches palette={p} />
                </Select.Item>
              ))}
            </Select.List>
          </Select.Popup>
        </Select.Positioner>
      </Select.Portal>
    </Select.Root>
  )
}
