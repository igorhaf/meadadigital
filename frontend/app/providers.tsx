'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'

import { ThemeModeProvider } from '@/components/theme-mode-provider'

/**
 * Providers client-side da aplicação: TanStack Query + modo de tema (claro/escuro).
 *
 * O QueryClient é criado via useState(() => new QueryClient()) — UMA instância por
 * montagem do componente no client, NÃO um singleton global. Singleton global
 * vazaria cache entre requests no SSR (um usuário veria dados de outro). Este é o
 * padrão oficial do TanStack para Next app router.
 *
 * ThemeModeProvider (camada 5.9) gerencia light/dark/system em runtime e persiste em
 * localStorage. O anti-flash no boot fica no <head> do app/layout.tsx (script inline),
 * antes da hidratação; aqui o provider só assume o controle reativo. É ORTOGONAL ao
 * ThemeProvider (paleta de marca do tenant) — um cuida do claro/escuro, o outro das cores.
 */
export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient())

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeModeProvider>{children}</ThemeModeProvider>
    </QueryClientProvider>
  )
}
