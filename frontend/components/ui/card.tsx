import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * Card — moldura consistente do painel (borda fina + bg-card + cantos arredondados +
 * padding generoso Notion-like). Substitui os `rounded-xl border ... bg-white` espalhados
 * (que quebravam dark mode). bg-card respeita o tema.
 */
export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('rounded-lg border border-border bg-card p-6', className)}>{children}</div>
  )
}

/**
 * Section — agrupa um pedaço de conteúdo dentro da página, com título + descrição
 * opcionais (estilo Notion: título médio, descrição em muted). Usado para quebrar forms
 * e telas longas em blocos lógicos.
 */
export function Section({
  title,
  description,
  actions,
  children,
  className,
}: {
  title?: string
  description?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cn('space-y-4', className)}>
      {(title || actions) && (
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            {title && <h2 className="text-base font-semibold text-foreground">{title}</h2>}
            {description && <p className="text-sm text-muted-foreground">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}
