'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'

/**
 * Providers client-side da aplicação. Hoje: TanStack Query.
 *
 * O QueryClient é criado via useState(() => new QueryClient()) — UMA instância por
 * montagem do componente no client, NÃO um singleton global. Singleton global
 * vazaria cache entre requests no SSR (um usuário veria dados de outro). Este é o
 * padrão oficial do TanStack para Next app router.
 */
export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient())

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
