'use client'

import { usePathname } from 'next/navigation'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

/**
 * Estado do sidebar colapsável (desktop). Dois MODOS, decididos pela rota:
 *
 * - **push** (padrão, todas as telas exceto CMS): a aba-balão é CLIQUE-TOGGLE; o sidebar aberto
 *   EMPURRA o conteúdo (faz parte do fluxo flex). A preferência aberto/fechado PERSISTE em
 *   localStorage e vale globalmente.
 * - **cms** (rotas /dashboard/cms*): a aba-balão é HOVER-PEEK (passar por cima abre, tirar recolhe,
 *   com atraso de ~200ms pra não piscar); o sidebar aberto SOBREPÕE o editor (overlay fixed, não
 *   reflui). Começa SEMPRE fechado ao entrar e NÃO persiste.
 *
 * Mobile não usa nada disto (mantém o drawer do AppShell).
 */

const STORAGE_KEY = 'meada.sidebar.collapsed'
const HOVER_CLOSE_DELAY_MS = 200

type SidebarMode = 'push' | 'cms'

type SidebarCtx = {
  mode: SidebarMode
  /** Sidebar visível no desktop? (no modo cms = espiando via hover). */
  open: boolean
  /** Clique na aba (modo push) — alterna e persiste. No modo cms é no-op (lá é hover). */
  toggle: () => void
  /** Handlers de hover (modo cms) — a aba e o sidebar chamam pra abrir/fechar com atraso. */
  onHoverEnter: () => void
  onHoverLeave: () => void
}

const Ctx = createContext<SidebarCtx | null>(null)

export function SidebarProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const mode: SidebarMode = pathname?.startsWith('/dashboard/cms') ? 'cms' : 'push'

  // Estado persistido (modo push). Inicia false (aberto) no SSR; hidrata do localStorage no client.
  const [collapsed, setCollapsed] = useState(false)
  // Estado de hover (modo cms). Independente do collapsed.
  const [peeking, setPeeking] = useState(false)
  const hoverTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Hidrata a preferência salva (só afeta o modo push).
  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(STORAGE_KEY)
      if (saved != null) setCollapsed(saved === '1')
    } catch {
      /* localStorage indisponível — ignora, usa default */
    }
  }, [])

  // Ao ENTRAR no CMS, o peek começa fechado (sempre). Limpa timers pendentes.
  useEffect(() => {
    if (mode === 'cms') {
      setPeeking(false)
      if (hoverTimer.current) clearTimeout(hoverTimer.current)
    }
  }, [mode])

  const toggle = useCallback(() => {
    if (mode !== 'push') return
    setCollapsed((c) => {
      const next = !c
      try {
        window.localStorage.setItem(STORAGE_KEY, next ? '1' : '0')
      } catch {
        /* ignora */
      }
      return next
    })
  }, [mode])

  const onHoverEnter = useCallback(() => {
    if (mode !== 'cms') return
    if (hoverTimer.current) clearTimeout(hoverTimer.current)
    setPeeking(true)
  }, [mode])

  const onHoverLeave = useCallback(() => {
    if (mode !== 'cms') return
    if (hoverTimer.current) clearTimeout(hoverTimer.current)
    hoverTimer.current = setTimeout(() => setPeeking(false), HOVER_CLOSE_DELAY_MS)
  }, [mode])

  const open = mode === 'cms' ? peeking : !collapsed

  const value = useMemo<SidebarCtx>(
    () => ({ mode, open, toggle, onHoverEnter, onHoverLeave }),
    [mode, open, toggle, onHoverEnter, onHoverLeave],
  )

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useSidebar(): SidebarCtx {
  const ctx = useContext(Ctx)
  if (!ctx) {
    throw new Error('useSidebar precisa estar dentro de <SidebarProvider>')
  }
  return ctx
}
