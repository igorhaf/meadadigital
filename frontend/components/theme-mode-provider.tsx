'use client'

import { createContext, useCallback, useContext, useEffect, useState } from 'react'

/**
 * Modo de tema CLARO/ESCURO (camada 5.9) — ortogonal à paleta de marca do tenant
 * (ThemeProvider, que injeta --palette-*). Aqui é só light vs dark vs system, alternando
 * a classe `dark` no <html> (globals.css usa @custom-variant dark (&:is(.dark *))).
 *
 * 3 estados:
 *   - 'light'  → remove a classe dark
 *   - 'dark'   → adiciona a classe dark
 *   - 'system' → segue prefers-color-scheme do SO (e reage a mudanças dele)
 *
 * Persistência: localStorage 'meada-theme'. O FLASH no boot é evitado por um script
 * inline no <head> (app/layout.tsx) que aplica a classe ANTES do React hidratar — este
 * provider só assume o controle em runtime e mantém o estado em sincronia.
 */
export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'meada-theme'

type ThemeModeContextValue = {
  mode: ThemeMode
  setMode: (mode: ThemeMode) => void
  /** Alterna em ciclo: light → dark → system → light. */
  cycle: () => void
}

const ThemeModeContext = createContext<ThemeModeContextValue | null>(null)

/** Resolve se a classe `dark` deve estar presente, dado o modo. */
function resolveIsDark(mode: ThemeMode): boolean {
  if (mode === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return mode === 'dark'
}

/** Aplica/remove a classe `dark` no <html> conforme o modo efetivo. */
function applyMode(mode: ThemeMode) {
  const root = document.documentElement
  root.classList.toggle('dark', resolveIsDark(mode))
}

export function ThemeModeProvider({ children }: { children: React.ReactNode }) {
  // Inicializa 'light' no SSR (default do produto — sempre foi claro; "seguir o SO" é
  // opt-in via toggle, não automático). O valor real do localStorage é lido no useEffect;
  // o script inline do layout já aplicou a classe correta antes da hidratação (sem flash).
  const [mode, setModeState] = useState<ThemeMode>('light')

  // Hidrata o estado a partir do localStorage no mount (client only). 'system' legado vira 'light'
  // (o produto só tem claro/escuro agora — resquício do toggle de 3 estados).
  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect -- hidratação do localStorage no mount (client only) é sync com sistema externo */
    const stored = localStorage.getItem(STORAGE_KEY) as ThemeMode | null
    if (stored === 'dark') {
      setModeState('dark')
    } else if (stored === 'system') {
      setModeState('light')
      localStorage.setItem(STORAGE_KEY, 'light')
    } else {
      setModeState('light')
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [])

  // Aplica a classe sempre que o modo muda.
  useEffect(() => {
    applyMode(mode)
  }, [mode])

  // Em modo 'system', reage a mudanças do SO ao vivo (sem precisar recarregar).
  useEffect(() => {
    if (mode !== 'system') return
    const mql = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => applyMode('system')
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [mode])

  const setMode = useCallback((next: ThemeMode) => {
    localStorage.setItem(STORAGE_KEY, next)
    setModeState(next)
  }, [])

  const cycle = useCallback(() => {
    // Alterna SOMENTE claro ↔ escuro (o 'system' foi removido do toggle — produto só tem 2 modos).
    // Se o estado vier de um valor antigo 'system' (localStorage legado), trata como claro e
    // alterna pra escuro.
    setModeState((prev) => {
      const next: ThemeMode = prev === 'dark' ? 'light' : 'dark'
      localStorage.setItem(STORAGE_KEY, next)
      return next
    })
  }, [])

  return (
    <ThemeModeContext.Provider value={{ mode, setMode, cycle }}>
      {children}
    </ThemeModeContext.Provider>
  )
}

export function useThemeMode(): ThemeModeContextValue {
  const ctx = useContext(ThemeModeContext)
  if (!ctx) {
    throw new Error('useThemeMode deve ser usado dentro de <ThemeModeProvider>')
  }
  return ctx
}
