'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

/**
 * Estado do sidebar colapsável global (desktop), modo PUSH único: a aba-balão é CLIQUE-TOGGLE; o
 * sidebar aberto EMPURRA o conteúdo (faz parte do fluxo flex). A preferência aberto/fechado PERSISTE
 * em localStorage e vale globalmente.
 *
 * (O CMS tem editor próprio tela-cheia e NÃO usa este sidebar — o AppShell esconde o sidebar global
 * nas rotas /dashboard/cms.) Mobile mantém o drawer do AppShell.
 */

const STORAGE_KEY = 'meada.sidebar.collapsed'

type SidebarCtx = {
  /** Sidebar visível no desktop? */
  open: boolean
  /** Clique na aba — alterna e persiste. */
  toggle: () => void
}

const Ctx = createContext<SidebarCtx | null>(null)

export function SidebarProvider({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)

  // Hidrata a preferência salva.
  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(STORAGE_KEY)
      // eslint-disable-next-line react-hooks/set-state-in-effect -- hidratação da preferência salva (localStorage) no mount
      if (saved != null) setCollapsed(saved === '1')
    } catch {
      /* localStorage indisponível — usa default */
    }
  }, [])

  const toggle = useCallback(() => {
    setCollapsed((c) => {
      const next = !c
      try {
        window.localStorage.setItem(STORAGE_KEY, next ? '1' : '0')
      } catch {
        /* ignora */
      }
      return next
    })
  }, [])

  const value = useMemo<SidebarCtx>(() => ({ open: !collapsed, toggle }), [collapsed, toggle])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useSidebar(): SidebarCtx {
  const ctx = useContext(Ctx)
  if (!ctx) {
    throw new Error('useSidebar precisa estar dentro de <SidebarProvider>')
  }
  return ctx
}
