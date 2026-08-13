'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { getProfileMatch } from '@/lib/api/admin/profiles'
import { GENERIC_PROFILE } from '@/lib/profiles/profile-type'
import { currentProfile, currentSubdomain, isUniversalSubdomain } from '@/lib/profiles/subdomain'
import { createClient } from '@/lib/supabase/client'

// Schema de LOGIN (não de signup): só verifica que há algo para enviar. Regras de
// força de senha são responsabilidade do Supabase Auth no cadastro — cravar min(8)
// aqui travaria login de senha legada curta sem motivo.
const loginSchema = z.object({
  email: z.string().email('Email inválido'),
  password: z.string().min(1, 'Informe a senha'),
})

type LoginForm = z.infer<typeof loginSchema>

/**
 * /login — entrada no painel. Login universal por email+senha (Supabase Auth).
 * Erro de credencial mostra SEMPRE a mesma mensagem genérica — não vaza a
 * existência da conta (anti user-enumeration).
 */
export default function LoginPage() {
  const router = useRouter()
  const [authError, setAuthError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  async function onSubmit(values: LoginForm) {
    setAuthError(null)
    const supabase = createClient()
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

    // Validação subdomínio×perfil (camada 7.0): num subdomínio de produto, o usuário só
    // prossegue se a empresa dele for daquele perfil. No universal ('meada'/localhost)
    // qualquer usuário passa. Mismatch → signOut + a MESMA mensagem genérica (não revela
    // que a conta existe nem em qual produto ela está — indistinguível de senha errada).
    const sub = currentSubdomain()
    if (!isUniversalSubdomain(sub)) {
      try {
        const result = await getProfileMatch(sub)
        if (!result.match) {
          await supabase.auth.signOut()
          setAuthError('Email ou senha inválidos.')
          return
        }
      } catch (err) {
        console.error('profile-match failed:', err)
        // Falha de rede no match não tranca o usuário fora do produto correto: deixa
        // passar (defensivo). O backend segue como barreira real por endpoint.
      }
    }

    router.push('/dashboard')
  }

  // Produto pelo subdomínio (camada 7.0): "Bem-vindo ao Sushi" etc. currentProfile() lê
  // window.location.hostname, que NÃO existe no SSR — padrão SSR-safe: começa no genérico
  // (igual ao servidor) e atualiza para o perfil real DEPOIS da montagem, no cliente.
  const [profile, setProfile] = useState(GENERIC_PROFILE)
  useEffect(() => {
    setProfile(currentProfile())
  }, [])

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl border bg-background p-6 shadow-sm">
        <h1 className="mb-1 text-lg font-semibold">{`Bem-vindo ao ${profile.productName}`}</h1>
        <p className="mb-6 text-sm text-muted-foreground">Entre no painel administrativo.</p>

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
            {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
          </div>

          <div className="space-y-1.5">
            <label htmlFor="password" className="text-sm font-medium">
              Senha
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
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
            {isSubmitting ? 'Enviando…' : 'Entrar'}
          </Button>
        </form>
      </div>
    </div>
  )
}
