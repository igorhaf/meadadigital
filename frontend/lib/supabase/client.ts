import { createBrowserClient } from '@supabase/ssr'

/**
 * Cliente Supabase para CLIENT components (browser). Usado por componentes
 * interativos como o form de login. Lê as envs públicas NEXT_PUBLIC_* (embarcadas
 * no bundle do browser — seguras porque a anon key é protegida pelo RLS).
 *
 * O createBrowserClient do @supabase/ssr gerencia a sessão via cookies (não
 * localStorage), para que o server (server components, middleware, layouts) também
 * consiga ler a mesma sessão.
 */
export function createClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
  if (!url || !anonKey) {
    throw new Error(
      'Supabase envs missing: NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY must be set in .env.local (see .env.example).',
    )
  }
  return createBrowserClient(url, anonKey)
}
