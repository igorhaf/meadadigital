import { Separator as SeparatorPrimitive } from '@base-ui/react/separator'

import { cn } from '@/lib/utils'

/**
 * Separador (régua) horizontal ou vertical. Wrapper fino sobre o primitivo do base-ui
 * (mantém a stack do projeto — base-ui, não Radix). Cor via --border, respeita o tema.
 */
export function Separator({
  orientation = 'horizontal',
  className,
}: {
  orientation?: 'horizontal' | 'vertical'
  className?: string
}) {
  return (
    <SeparatorPrimitive
      orientation={orientation}
      className={cn(
        'shrink-0 bg-border',
        orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px',
        className,
      )}
    />
  )
}
