'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { createClient } from '@/lib/supabase/client'

/**
 * Botão "Sair" reutilizável (extraído do dashboard do 4.0). Usado em todas as telas
 * protegidas do painel (hub, companies, e futuras 4.4/4.5).
 *
 * Comportamento preservado do 4.0: chama supabase.auth.signOut(); mesmo se falhar,
 * empurra para /login (melhor sair da tela protegida do que ficar num estado
 * meio-logado). variant="outline" — é ação secundária, não a CTA principal.
 */
export function SignOutButton() {
  const router = useRouter()
  const [signingOut, setSigningOut] = useState(false)

  async function handleSignOut() {
    setSigningOut(true)
    const supabase = createClient()
    const { error } = await supabase.auth.signOut()
    if (error) {
      console.error('signOut failed:', error.message)
    }
    router.push('/login')
  }

  return (
    <Button variant="outline" onClick={handleSignOut} disabled={signingOut}>
      {signingOut ? 'Saindo…' : 'Sair'}
    </Button>
  )
}
