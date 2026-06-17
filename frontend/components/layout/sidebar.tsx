'use client'

import { MessagesSquare } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

import { getNavForProfile } from './nav-config'
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
  onNavigate,
}: {
  role: 'super_admin' | 'tenant_admin' | undefined
  profileId?: string | null
  onNavigate?: () => void
}) {
  const pathname = usePathname()
  const isSuperAdmin = role === 'super_admin'

  // Nav por perfil (camada 7.1): o sushi ganha "Restaurante" (Cardápio/Pedidos). Filtra por
  // papel: grupo superAdminOnly só para super_admin; demais só para tenant.
  const groups = getNavForProfile(profileId).filter((g) =>
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
 * Sidebar fixo do desktop (>= md). Largura fixa, borda à direita, scroll próprio. No mobile
 * fica escondido (o drawer via Sheet assume — ver AppShell).
 */
export function Sidebar({
  role,
  productName,
  profileId,
}: {
  role: 'super_admin' | 'tenant_admin' | undefined
  productName?: string
  profileId?: string | null
}) {
  return (
    <aside className="hidden w-64 shrink-0 flex-col border-r border-border bg-background md:flex">
      <SidebarBrand productName={productName} />
      <div className="flex-1 overflow-y-auto">
        <SidebarNav role={role} profileId={profileId} />
      </div>
    </aside>
  )
}
