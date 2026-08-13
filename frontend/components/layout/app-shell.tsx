'use client'

import { useQuery } from '@tanstack/react-query'
import { Menu as MenuIcon, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'

import { Sidebar, SidebarBrand, SidebarNav } from './sidebar'
import { SidebarProvider } from './sidebar-context'
import { SidebarTab } from './sidebar-tab'
import { UserDropdown } from './user-dropdown'

/**
 * AppShell (camada UI) — shell do painel: sidebar fixo (desktop) + drawer (mobile) +
 * header global (hamburger + theme toggle + user dropdown) + área de conteúdo. Envolve
 * todas as telas protegidas (montado no layout (protected)).
 *
 * me vem do GET /admin/me (TanStack Query, mesma queryKey ['me'] usada nas telas → cache
 * compartilhado, sem fetch duplo). O papel decide quais grupos o sidebar mostra.
 *
 * O drawer mobile é um overlay + painel deslizante hand-rolled (mesmo padrão pragmático do
 * Modal do projeto), fechando ao navegar (onNavigate) e no overlay/X.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <SidebarProvider>
      <div className="flex min-h-screen bg-background">
        {/* Sidebar desktop COLAPSÁVEL — push (padrão): empurra o conteúdo, clique-toggle. */}
        <Sidebar
          role={me?.role}
          productName={me?.productName}
          profileId={me?.profileId}
          features={me?.features}
        />

        {/* Aba-balão (desktop): clique-toggle (push). Sempre visível. */}
        <SidebarTab />

        {/* Drawer mobile */}
        {drawerOpen && (
          <div className="fixed inset-0 z-50 md:hidden">
            <div
              className="absolute inset-0 bg-black/40"
              onClick={() => setDrawerOpen(false)}
              aria-hidden
            />
            <div className="absolute inset-y-0 left-0 flex w-72 flex-col bg-sidebar text-sidebar-foreground shadow-xl">
              <div className="flex items-center justify-between px-4 py-3">
                <SidebarBrand productName={me?.productName} />
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Fechar menu"
                  onClick={() => setDrawerOpen(false)}
                >
                  <X className="size-5" />
                </Button>
              </div>
              <SidebarNav
                role={me?.role}
                profileId={me?.profileId}
                features={me?.features}
                onNavigate={() => setDrawerOpen(false)}
              />
            </div>
          </div>
        )}

        {/* Área principal */}
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex items-center justify-between gap-2 border-b border-border px-4 py-2">
            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              aria-label="Abrir menu"
              onClick={() => setDrawerOpen(true)}
            >
              <MenuIcon className="size-5" />
            </Button>
            <div className="flex flex-1 items-center justify-end gap-2">
              <ThemeToggle />
              <UserDropdown me={me} />
            </div>
          </header>
          <main className="min-h-0 flex-1 overflow-y-auto">
            <div className="mx-auto w-full max-w-6xl p-4 md:p-6">{children}</div>
          </main>
        </div>
      </div>
    </SidebarProvider>
  )
}
