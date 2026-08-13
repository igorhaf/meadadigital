'use client'

import { Menu, X } from 'lucide-react'

import { cn } from '@/lib/utils'

import { useSidebar } from './sidebar-context'

/**
 * Aba-balão do sidebar global (desktop) — "aba de navegador vertical" grudada na borda, SEMPRE
 * visível. CLIQUE alterna abrir/fechar (e persiste). Acompanha a borda: colada no sidebar (left-64)
 * quando aberto, na borda esquerda (left-0) quando fechado. Some no mobile (md:flex).
 */
export function SidebarTab() {
  const { open, toggle } = useSidebar()
  const leftClass = open ? 'left-64' : 'left-0'

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={open ? 'Recolher menu' : 'Abrir menu'}
      aria-expanded={open}
      className={cn(
        'fixed top-1/2 z-[60] hidden -translate-y-1/2 items-center justify-center md:flex',
        'h-16 w-6 rounded-r-xl border border-l-0 border-border bg-background text-muted-foreground shadow-md',
        'transition-[left] duration-200 ease-out hover:bg-muted hover:text-foreground',
        leftClass,
      )}
    >
      {open ? <X className="size-4" /> : <Menu className="size-4" />}
    </button>
  )
}
