import { createServerClient } from '@supabase/ssr'
import { cookies } from 'next/headers'

/**
 * Cliente Supabase para SERVER components, layouts e middleware. Lê/escreve a
 * sessão nos cookies da request via o adaptador do Next.
 *
 * É ASYNC porque cookies() do Next 16 retorna Promise<ReadonlyRequestCookies>
 * (mudou de síncrono → async no Next 15+). Sempre: const supabase = await createClient().
 *
 * Padrão getAll/setAll (API atual do @supabase/ssr 0.10.x; get/set/remove
 * individuais estão deprecados). O try/catch no setAll cobre o caso de ser chamado
 * de um Server Component (onde escrever cookie não é permitido) — nesse caso o
 * refresh de sessão é feito pelo middleware, então ignorar aqui é seguro.
 */
export async function createClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
  if (!url || !anonKey) {
    throw new Error(
      'Supabase envs missing: NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY must be set in .env.local (see .env.example).',
    )
  }

  const cookieStore = await cookies()

  return createServerClient(url, anonKey, {
    cookies: {
      getAll() {
        return cookieStore.getAll()
      },
      setAll(cookiesToSet) {
        try {
          cookiesToSet.forEach(({ name, value, options }) => cookieStore.set(name, value, options))
        } catch {
          // Chamado de um Server Component (cookies read-only). O middleware é quem
          // persiste o refresh da sessão — ignorar aqui é seguro e esperado.
        }
      },
    },
  })
}
