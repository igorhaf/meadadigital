'use client'

import { useQuery } from '@tanstack/react-query'
import { Menu as MenuIcon, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { AnnouncementBanner } from '@/components/announcement-banner'
import { GlobalSearch } from '@/components/global-search'
import { RealtimeNotifications } from '@/components/realtime-notifications'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import { Sidebar, SidebarBrand, SidebarNav } from './sidebar'
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
 * Modal do projeto), fechando ao navegar (onNavigate) e no overlay/X. GlobalSearch (Cmd+K)
 * e RealtimeNotifications seguem montados aqui (uma vez), como antes.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <div className="flex min-h-screen bg-background">
      <GlobalSearch />
      <RealtimeNotifications />

      {/* Sidebar desktop — productName + nav mudam por perfil (camada 7.0/7.1). features (9.0):
          plumbing pra SM-M gatear itens de nav por feature flag. */}
      <Sidebar role={me?.role} productName={me?.productName} profileId={me?.profileId} features={me?.features} />

      {/* Drawer mobile */}
      {drawerOpen && (
        <div className="fixed inset-0 z-50 md:hidden">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setDrawerOpen(false)}
            aria-hidden="true"
          />
          <div className="absolute inset-y-0 left-0 flex w-72 max-w-[80%] flex-col border-r border-border bg-background shadow-xl">
            <div className="flex items-center justify-between pr-3">
              <SidebarBrand productName={me?.productName} />
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setDrawerOpen(false)}
                aria-label="Fechar menu"
              >
                <X className="size-5" />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto">
              <SidebarNav role={me?.role} profileId={me?.profileId} features={me?.features} onNavigate={() => setDrawerOpen(false)} />
            </div>
          </div>
        </div>
      )}

      {/* Coluna de conteúdo */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border px-4 py-4 md:px-8">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              onClick={() => setDrawerOpen(true)}
              aria-label="Abrir menu"
            >
              <MenuIcon className="size-5" />
            </Button>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <UserDropdown me={me} />
          </div>
        </header>

        {/* Banner de anúncios (camada 6.7): entre o header global e o conteúdo. Some sozinho
            quando não há anúncios ativos não-dispensados. */}
        <AnnouncementBanner />

        <main className="flex-1 overflow-x-hidden">
          <div className="mx-auto max-w-6xl px-4 py-8 md:px-8">{children}</div>
        </main>
      </div>
    </div>
  )
}
