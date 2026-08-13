'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { Button } from '@/components/ui/button'
import { countUnreadConversations } from '@/lib/supabase/conversations'

/**
 * Link "Conversas" do menu do tenant com badge de conversas pendentes (camada 5.10).
 * Polling a cada 10s via TanStack Query (queryKey separada da lista de conversas, não
 * compete com o refetchInterval 5s da tela /dashboard/conversations).
 *
 * Quando count = 0 (ou ainda carregando): badge some. Quando > 99: mostra "99+".
 * Badge usa bg-primary = paleta da empresa (coerente com a identidade, não destoa).
 */
export function ConversationsNavLink() {
  const { data: count } = useQuery({
    queryKey: ['unread-conversations-count'],
    queryFn: countUnreadConversations,
    refetchInterval: 10000,
  })

  return (
    <Link href="/dashboard/conversations">
      <Button variant="outline" className="relative">
        Conversas
        {count != null && count > 0 && (
          <span className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-primary text-[10px] font-medium text-primary-foreground">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </Button>
    </Link>
  )
}
