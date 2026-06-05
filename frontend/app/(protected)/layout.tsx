import { redirect } from 'next/navigation'

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
 * Sem header/nav/sidebar aqui: este layout é só passagem. UI de shell entra em
 * sub-fase futura quando houver telas de verdade.
 */
export default async function ProtectedLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const supabase = await createClient()
  const {
    data: { user },
    error,
  } = await supabase.auth.getUser()

  if (error || !user) {
    redirect('/login')
  }

  return <>{children}</>
}
