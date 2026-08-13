import { redirect } from 'next/navigation'

import { AppShell } from '@/components/layout/app-shell'
import { ThemeProvider } from '@/components/theme-provider'
import { createClient } from '@/lib/supabase/server'

/**
 * Barreira de autorização do grupo (protected). Server component: roda no servidor
 * ANTES de qualquer JSX renderizar, então não há flash de UI protegida antes do
 * redirect.
 *
 * getUser() (não getSession()): contata o Auth server e revalida — getSession lê o
 * cookie cru, falsificável (ver README do @supabase/ssr). Para autorização, sempre
 * getUser.
 *
 * Qualquer falha (error não-null OU user null) → redirect para /login. Conservador:
 * não distingue "erro de rede" de "sem sessão" — qualquer dúvida manda para o login.
 *
 * É a 2ª chamada a getUser por request em rotas protegidas (a 1ª é o middleware, que
 * refresca o cookie). Trade aceito: middleware = preventivo (refresh), layout =
 * autoritativo (autorização). É o pattern canônico; não otimizamos agora.
 *
 * ThemeProvider (client) envolve os filhos para injetar as CSS vars do tema (camada
 * 5.0). Importado direto: um client component dentro de um server component cria o
 * boundary corretamente — o layout segue server (mantém a barreira getUser), e o
 * 'use client' mora no ThemeProvider. Sem next/dynamic: o provider é SSR-safe (no
 * server renderiza só os filhos; o useEffect que toca o document só roda no client) e
 * já aplica meada-default enquanto a query carrega (anti-flash).
 *
 * Sem header/nav/sidebar aqui: este layout é só passagem. UI de shell entra em
 * sub-fase futura quando houver telas de verdade.
 *
 * GlobalSearch (paleta Cmd+K, #84) e RealtimeNotifications (toasts de nova conversa,
 * #83) são montados aqui (uma vez só) para ficarem disponíveis em todas as telas do
 * dashboard. Ambos são client components ('use client') — o boundary fica neles; o
 * layout segue server (mantém a barreira getUser).
 */
export default async function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const supabase = await createClient()
  const {
    data: { user },
    error,
  } = await supabase.auth.getUser()

  if (error || !user) {
    redirect('/login')
  }

  return (
    <ThemeProvider>
      <AppShell>{children}</AppShell>
    </ThemeProvider>
  )
}
