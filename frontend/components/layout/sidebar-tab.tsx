'use client'

import { Menu, X } from 'lucide-react'

import { cn } from '@/lib/utils'
import { useSidebar } from './sidebar-context'

/**
 * Aba-balão do sidebar (desktop) — "aba de navegador na vertical" grudada na borda do sidebar,
 * SEMPRE visível. Acompanha a borda direita do sidebar: quando aberto, fica colada nele; quando
 * fechado, fica na borda esquerda da tela.
 *
 * Comportamento por modo (ver {@link useSidebar}):
 * - **push**: CLIQUE alterna abrir/fechar (e persiste).
 * - **cms**: HOVER — passar o mouse por cima abre (peek), tirar recolhe (~200ms). Clique não faz nada.
 *
 * Posicionada `fixed` à esquerda, centralizada verticalmente, acima de tudo (z alto). Esconde no
 * mobile (`md:flex`) — lá o drawer do AppShell assume.
 */
export function SidebarTab() {
  const { mode, open, toggle, onHoverEnter, onHoverLeave } = useSidebar()

  // posição horizontal da aba: colada na borda direita do sidebar (w-64 = 16rem) quando aberto,
  // na borda esquerda da tela quando fechado.
  const leftClass = open ? 'left-64' : 'left-0'

  return (
    <button
      type="button"
      onClick={toggle}
      onMouseEnter={onHoverEnter}
      onMouseLeave={onHoverLeave}
      aria-label={open ? 'Recolher menu' : 'Abrir menu'}
      aria-expanded={open}
      className={cn(
        'fixed top-1/2 z-[60] hidden -translate-y-1/2 items-center justify-center md:flex',
        'h-16 w-6 rounded-r-xl border border-l-0 border-border bg-background text-muted-foreground shadow-md',
        'transition-[left] duration-200 ease-out hover:bg-muted hover:text-foreground',
        leftClass,
        mode === 'cms' && 'cursor-default',
      )}
    >
      {open ? <X className="size-4" /> : <Menu className="size-4" />}
    </button>
  )
}
