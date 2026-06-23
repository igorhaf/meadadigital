import { type EmailOtpType } from '@supabase/supabase-js'
import { type NextRequest, NextResponse } from 'next/server'

import { createClient } from '@/lib/supabase/server'

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
  const next = searchParams.get('next') ?? '/dashboard'

  if (!tokenHash) {
    return NextResponse.redirect(new URL('/login?error=missing_token', request.url))
  }

  const supabase = await createClient()
  const { error } = await supabase.auth.verifyOtp({ type, token_hash: tokenHash })
  if (error) {
    return NextResponse.redirect(new URL('/login?error=invalid_link', request.url))
  }

  return NextResponse.redirect(new URL(next, request.url))
}
