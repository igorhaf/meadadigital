'use client'

import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { getMe } from '@/lib/api/me'
import { createClient } from '@/lib/supabase/client'

/** Toast efêmero exibido por 4s ao chegar uma nova conversa. */
type Toast = { id: number; text: string }

/**
 * Notificações em tempo real (camada 5.22 #83). Componente global montado uma vez no
 * layout (protected). Assina mudanças na tabela conversations via Supabase Realtime,
 * filtrando pela empresa do tenant, e mostra um toast simples quando nasce uma conversa.
 *
 * <p>Só assina para tenant-admin (precisa do companyId, vindo de getMe). Super-admin não
 * tem empresa → não assina. Toast inline (div fixa por 4s) — sem lib de toast, de propósito.
 *
 * <p>Se o Realtime não estiver habilitado no projeto Supabase, o subscribe simplesmente
 * não dispara eventos (no-op silencioso) — aceitável nesta fase. Todo o setup vai dentro de
 * try/catch para que qualquer erro do canal não derrube a árvore de componentes.
 */
export function RealtimeNotifications() {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const companyId = me?.role === 'tenant_admin' ? me.companyId : null
  const [toasts, setToasts] = useState<Toast[]>([])

  useEffect(() => {
    if (!companyId) return

    const supabase = createClient()
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let channel: any

    try {
      channel = supabase
        .channel('conversations')
        .on(
          'postgres_changes',
          {
            event: 'INSERT',
            schema: 'public',
            table: 'conversations',
            filter: `company_id=eq.${companyId}`,
          },
          () => {
            const id = Date.now()
            setToasts((prev) => [...prev, { id, text: 'Nova conversa recebida' }])
            // Remove o toast após 4s.
            setTimeout(() => {
              setToasts((prev) => prev.filter((t) => t.id !== id))
            }, 4000)
          },
        )
        .subscribe()
    } catch (err) {
      console.error('Realtime subscribe failed (ignorado):', err)
    }

    return () => {
      try {
        if (channel) supabase.removeChannel(channel)
      } catch (err) {
        console.error('Realtime unsubscribe failed (ignorado):', err)
      }
    }
  }, [companyId])

  if (toasts.length === 0) return null

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          className="rounded-lg border border-border bg-white px-4 py-3 text-sm shadow-lg"
        >
          {t.text}
        </div>
      ))}
    </div>
  )
}
