import { cn } from '@/lib/utils'

/**
 * Skeleton loader — bloco cinza pulsante que ocupa o espaço do conteúdo enquanto
 * carrega (substitui "Carregando…" em texto plano, padrão Linear/Notion). Usa bg-muted
 * + animate-pulse; respeita o tema (claro/escuro/paleta) via a var --muted.
 */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-muted', className)} aria-hidden="true" />
}

/**
 * Esqueleto de página padrão — um cabeçalho + algumas linhas. Reusável nas telas
 * enquanto a query inicial resolve, no lugar de spinner/texto.
 */
export function PageSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Carregando">
      <div className="space-y-2">
        <Skeleton className="h-7 w-48" />
        <Skeleton className="h-4 w-72" />
      </div>
      <div className="space-y-3 rounded-lg border border-border bg-card p-6">
        <Skeleton className="h-5 w-full" />
        <Skeleton className="h-5 w-5/6" />
        <Skeleton className="h-5 w-4/6" />
        <Skeleton className="h-5 w-3/6" />
      </div>
    </div>
  )
}
