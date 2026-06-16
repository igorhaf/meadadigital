import Link from 'next/link'
import type { ReactNode } from 'react'

/**
 * PageHeader — cabeçalho padrão de toda tela do painel (camada UI). Substitui o cabeçalho
 * repetido (h1 + botões) que vivia em cada página antes do refator. Fica DENTRO da área de
 * conteúdo, abaixo do header global (que tem theme toggle + user dropdown).
 *
 * @param title      título da página (h1)
 * @param description subtítulo opcional (muted)
 * @param breadcrumb trilha opcional [{label, href?}] — última entrada sem href = atual.
 *                   Renderizado acima do título em sub-páginas (ex.: "Conversas / João").
 * @param actions    botões/ações à direita (ex.: "Novo convite", "Exportar PDF")
 */
export function PageHeader({
  title,
  description,
  breadcrumb,
  actions,
}: {
  title: string
  description?: string
  breadcrumb?: { label: string; href?: string }[]
  actions?: ReactNode
}) {
  return (
    <div className="mb-8 space-y-2">
      {breadcrumb && breadcrumb.length > 0 && (
        <nav className="flex items-center gap-1.5 text-sm text-muted-foreground" aria-label="Trilha">
          {breadcrumb.map((item, i) => (
            <span key={i} className="flex items-center gap-1.5">
              {i > 0 && <span aria-hidden="true">/</span>}
              {item.href ? (
                <Link href={item.href} className="hover:text-foreground">
                  {item.label}
                </Link>
              ) : (
                <span className="text-foreground">{item.label}</span>
              )}
            </span>
          ))}
        </nav>
      )}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-xl font-semibold text-foreground">{title}</h1>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  )
}
