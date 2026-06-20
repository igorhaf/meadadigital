'use client'

import { MessagesSquare } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

import { getNavForProfile } from './nav-config'
import { useSidebar } from './sidebar-context'
import { cn } from '@/lib/utils'

/**
 * Marca o item ativo: match exato para /dashboard (senão a Início ficaria sempre ativa),
 * prefixo para as demais (o detalhe /conversations/[id] mantém Conversas ativa).
 */
function isActive(pathname: string, href: string): boolean {
  if (href === '/dashboard') {
    return pathname === '/dashboard'
  }
  return pathname === href || pathname.startsWith(href + '/')
}

/**
 * Conteúdo da navegação (grupos + itens) — compartilhado entre o sidebar fixo do desktop
 * e o drawer mobile. Filtra por papel: grupo superAdminOnly só para super_admin; os demais
 * grupos só para tenant (super-admin vê apenas Admin). onNavigate fecha o drawer no mobile.
 */
export function SidebarNav({
  role,
  profileId,
  features,
  onNavigate,
}: {
  role: 'super_admin' | 'tenant_admin' | undefined
  profileId?: string | null
  features?: Record<string, boolean>
  onNavigate?: () => void
}) {
  const pathname = usePathname()
  const isSuperAdmin = role === 'super_admin'

  // Nav por perfil (camada 7.1): o sushi ganha "Restaurante" (Cardápio/Pedidos). Filtra por
  // papel: grupo superAdminOnly só para super_admin; demais só para tenant. features (camada 9.0)
  // é plumbing pra SM-M gatear itens por feature flag (ex.: CMS atrás de hasFeature('cms')).
  const groups = getNavForProfile(profileId, features).filter((g) =>
    g.superAdminOnly ? isSuperAdmin : !isSuperAdmin,
  )

  return (
    <nav className="flex flex-col gap-6 px-3 py-4">
      {groups.map((group) => (
        <div key={group.heading} className="space-y-1">
          <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {group.heading}
          </p>
          {group.items.map((item) => {
            const active = isActive(pathname, item.href)
            const Icon = item.icon
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  active
                    ? 'bg-accent font-medium text-accent-foreground'
                    : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground',
                )}
              >
                <Icon className="size-4 shrink-0" />
                {item.label}
              </Link>
            )
          })}
        </div>
      ))}
    </nav>
  )
}

/** Logo da marca no topo do sidebar/drawer. */
/** Marca do topo do sidebar. productName é o "produto" do perfil (camada 7.0); default Meada. */
export function SidebarBrand({ productName = 'Meada' }: { productName?: string }) {
  return (
    <Link
      href="/dashboard"
      className="flex items-center gap-2 px-6 py-5 text-base font-semibold text-foreground"
    >
      <MessagesSquare className="size-5 text-primary" />
      {productName}
    </Link>
  )
}

/**
 * Sidebar do desktop (>= md), COLAPSÁVEL (ver {@link useSidebar}). Dois modos:
 *
 * - **push**: faz parte do fluxo flex; aberto = `w-64` (empurra o conteúdo), fechado = `w-0` (some).
 * - **cms**: `position: fixed` por cima do conteúdo (OVERLAY, não reflui o editor); aparece/some por
 *   hover (peek). No fluxo flex ele ocupa `w-0` (não empurra nada).
 *
 * A largura interna do painel é sempre 16rem; o colapso é por `w-0 + overflow-hidden` no wrapper.
 * No mobile fica escondido (o drawer do AppShell assume).
 */
export function Sidebar({
  role,
  productName,
  profileId,
  features,
}: {
  role: 'super_admin' | 'tenant_admin' | undefined
  productName?: string
  profileId?: string | null
  features?: Record<string, boolean>
}) {
  const { mode, open, onHoverEnter, onHoverLeave } = useSidebar()
  const overlay = mode === 'cms'

  return (
    <aside
      onMouseEnter={onHoverEnter}
      onMouseLeave={onHoverLeave}
      className={cn(
        'hidden shrink-0 overflow-hidden border-border bg-background transition-[width] duration-200 ease-out md:flex md:flex-col',
        open ? 'w-64 border-r' : 'w-0 border-r-0',
        // modo CMS: sai do fluxo e flutua por cima (overlay). w-0 acima garante que não empurra nada.
        overlay && 'fixed inset-y-0 left-0 z-50 shadow-xl',
      )}
    >
      {/* painel de 16rem fixo: o colapso é só no wrapper (w-0), o conteúdo não "esmaga" */}
      <div className="flex h-full w-64 flex-col">
        <SidebarBrand productName={productName} />
        <div className="flex-1 overflow-y-auto">
          <SidebarNav role={role} profileId={profileId} features={features} />
        </div>
      </div>
    </aside>
  )
}
