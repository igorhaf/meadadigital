import { type EmailOtpType } from '@supabase/supabase-js'
import { type NextRequest, NextResponse } from 'next/server'

import { createClient } from '@/lib/supabase/server'

/**
 * Sanitiza o destino do redirect pós-login: aceita SÓ path relativo do próprio site.
 * Bloqueia open redirect — `https://evil.com`, `//evil.com` e `/\evil.com` escapariam do
 * origin via `new URL(next, origin)`. Exige começar com '/' e o 2º char NÃO ser '/' nem '\'.
 * Qualquer coisa fora disso cai no default seguro '/dashboard'.
 */
function safeNext(raw: string | null): string {
  if (!raw || raw[0] !== '/' || raw[1] === '/' || raw[1] === '\\') {
    return '/dashboard'
  }
  return raw
}

/**
 * Callback de verificação de OTP/magic link (padrão @supabase/ssr).
 * GET /auth/confirm?token_hash=…&type=…&next=/dashboard
 *
 * Usado pelo "entrar como empresa" do super-admin: o backend gera um magic link
 * (token de uso único) para o admin da empresa; a nova aba abre aqui, verifyOtp troca
 * o token por uma sessão Supabase real (gravada nos cookies), e redireciona pro painel.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url)
  const tokenHash = searchParams.get('token_hash')
  const type = (searchParams.get('type') ?? 'email') as EmailOtpType
  const next = safeNext(searchParams.get('next'))

  if (!tokenHash) {
    return NextResponse.redirect(new URL('/login?error=missing_token', request.url))
  }

  // Base do redirect = host REAL do request (header Host), não request.url (que internamente
  // pode ser localhost atrás do proxy/porta 80). Sem isso, o redirect levaria pra localhost —
  // outro domínio — e o cookie de sessão (setado no host atual) não acompanharia → cai no login.
  const host = request.headers.get('host') ?? new URL(request.url).host
  const proto = request.headers.get('x-forwarded-proto') ?? 'http'
  const origin = `${proto}://${host}`

  const supabase = await createClient()
  const { error } = await supabase.auth.verifyOtp({ type, token_hash: tokenHash })
  if (error) {
    return NextResponse.redirect(new URL('/login?error=invalid_link', origin))
  }

  return NextResponse.redirect(new URL(next, origin))
}
