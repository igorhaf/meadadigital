'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/client'
import { acceptInvitation } from '@/lib/api/invitations'
import { createClient } from '@/lib/supabase/client'

// Schema de LOGIN (não de signup): só verifica que há algo para enviar. Regras de
// força de senha são responsabilidade do Supabase Auth no cadastro — cravar min(8)
// aqui travaria login de senha legada curta sem motivo.
const loginSchema = z.object({
  email: z.string().email('Email inválido'),
  password: z.string().min(1, 'Informe a senha'),
})

type LoginForm = z.infer<typeof loginSchema>

// Mensagens dos reasons de erro do accept (camada 5.16 #6) — o backend manda .reason.
const ACCEPT_ERROR_MESSAGES: Record<string, string> = {
  invitation_not_found: 'Convite não encontrado.',
  invitation_expired: 'Este convite expirou.',
  invitation_already_used: 'Este convite já foi usado.',
  invitation_email_mismatch:
    'Este convite foi enviado para outro email. Use o email exato do convite.',
}

function LoginInner() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const inviteToken = searchParams.get('invite')
  const isInvite = !!inviteToken

  const [authError, setAuthError] = useState<string | null>(null)
  // Quando há convite, o convidado pode precisar CRIAR conta. Toggle entre signin/signup.
  const [mode, setMode] = useState<'signin' | 'signup'>('signin')

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  async function onSubmit(values: LoginForm) {
    setAuthError(null)
    const supabase = createClient()

    // Signup (só no fluxo de convite) ou signin. No signup, o Supabase pode exigir
    // confirmação de email conforme a config do projeto; em dev com auto-confirm, a
    // sessão já vem ativa. Se não vier sessão (precisa confirmar), avisamos.
    if (isInvite && mode === 'signup') {
      const { data, error } = await supabase.auth.signUp({
        email: values.email,
        password: values.password,
      })
      if (error) {
        console.error('signup failed:', error.message)
        setAuthError('Não foi possível criar a conta. Ela pode já existir — tente entrar.')
        return
      }
      if (!data.session) {
        setAuthError(
          'Conta criada. Confirme seu email (se exigido) e depois entre para aceitar o convite.',
        )
        return
      }
    } else {
      const { error } = await supabase.auth.signInWithPassword({
        email: values.email,
        password: values.password,
      })
      if (error) {
        // Mensagem GENÉRICA ao usuário (não vaza existência de conta / enumeration).
        console.error('login failed:', error.message)
        setAuthError('Email ou senha inválidos.')
        return
      }
    }

    // Sessão ativa neste ponto. Se há convite, aceita ANTES de ir ao dashboard.
    if (isInvite && inviteToken) {
      try {
        await acceptInvitation(inviteToken)
      } catch (err) {
        const reason = err instanceof ApiError ? err.reason : 'unknown'
        console.error('acceptInvitation failed:', reason)
        setAuthError(
          ACCEPT_ERROR_MESSAGES[reason] ??
            'Não foi possível aceitar o convite. Tente novamente.',
        )
        return
      }
    }

    router.push('/dashboard')
  }

  const title = isInvite ? 'Aceitar convite' : 'Meada WhatsApp'
  const subtitle = isInvite
    ? mode === 'signup'
      ? 'Crie sua conta com o email do convite para entrar.'
      : 'Entre com o email do convite para aceitá-lo.'
    : 'Entre no painel administrativo.'

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl border bg-background p-6 shadow-sm">
        <h1 className="mb-1 text-lg font-semibold">{title}</h1>
        <p className="mb-6 text-sm text-muted-foreground">{subtitle}</p>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <label htmlFor="email" className="text-sm font-medium">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              aria-invalid={!!errors.email}
              className="w-full rounded-lg border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring aria-invalid:border-destructive"
              {...register('email')}
            />
            {errors.email && (
              <p className="text-xs text-destructive">{errors.email.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label htmlFor="password" className="text-sm font-medium">
              Senha
            </label>
            <input
              id="password"
              type="password"
              autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
              aria-invalid={!!errors.password}
              className="w-full rounded-lg border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring aria-invalid:border-destructive"
              {...register('password')}
            />
            {errors.password && (
              <p className="text-xs text-destructive">{errors.password.message}</p>
            )}
          </div>

          {authError && <p className="text-sm text-destructive">{authError}</p>}

          <Button type="submit" disabled={isSubmitting} className="w-full">
            {isSubmitting
              ? 'Enviando…'
              : isInvite && mode === 'signup'
                ? 'Criar conta e aceitar'
                : isInvite
                  ? 'Entrar e aceitar'
                  : 'Entrar'}
          </Button>

          {isInvite && (
            <button
              type="button"
              className="w-full text-center text-xs text-muted-foreground hover:underline"
              onClick={() => {
                setAuthError(null)
                setMode((m) => (m === 'signin' ? 'signup' : 'signin'))
              }}
            >
              {mode === 'signin'
                ? 'Ainda não tem conta? Criar conta'
                : 'Já tem conta? Entrar'}
            </button>
          )}
        </form>
      </div>
    </div>
  )
}

/**
 * /login — entrada no painel. Sem query: login normal. Com ?invite={token} (camada 5.16
 * #6): fluxo de convite — permite criar conta (signup) e, após autenticar, chama
 * acceptInvitation antes de ir ao dashboard.
 *
 * useSearchParams exige Suspense boundary no App Router (a página é client, mas a leitura
 * dos params suspende no prerender) — por isso o conteúdo fica em LoginInner sob Suspense.
 */
export default function LoginPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center p-4" />}>
      <LoginInner />
    </Suspense>
  )
}
