import type { ReactNode } from 'react'

/**
 * Estado vazio elevado (camada 5.8): ícone + título + descrição educativa + ação
 * opcional (botão de criar). Centralizado, padding generoso, dentro de uma moldura
 * (card) coerente com a DataTable. Renderizado NO LUGAR da DataTable quando a lista
 * está vazia — a tela decide via data.length === 0.
 *
 * action é opcional: telas de CRUD passam um botão "Criar primeiro X"; conversas (que o
 * cliente cria via WhatsApp, não o tenant) passa só ícone + texto, sem ação.
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: ReactNode
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-border bg-card px-6 py-16 text-center">
      <div className="text-muted-foreground [&_svg]:size-10" aria-hidden="true">
        {icon}
      </div>
      <h3 className="text-base font-medium text-foreground">{title}</h3>
      <p className="max-w-sm text-sm text-muted-foreground">{description}</p>
      {action && <div className="mt-1">{action}</div>}
    </div>
  )
}
