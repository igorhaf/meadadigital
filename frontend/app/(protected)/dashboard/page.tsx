'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { createClient } from '@/lib/supabase/client'

/**
 * Placeholder do dashboard (sub-fase 4.0). Única interatividade real: o botão Sair,
 * que existe para o smoke test do 4.0 (passo 5: logout limpa a sessão). Telas de
 * verdade (listas, CRUD, conversas) entram a partir do 4.1 — nada de fetch/TanStack
 * aqui ainda.
 */
export default function DashboardPage() {
  const router = useRouter()
  const [signingOut, setSigningOut] = useState(false)

  async function handleSignOut() {
    setSigningOut(true)
    const supabase = createClient()
    const { error } = await supabase.auth.signOut()
    if (error) {
      // Mesmo com erro, empurra para /login: melhor sair da tela protegida do que
      // ficar preso num estado meio-logado. Detalhe real no console.
      console.error('signOut failed:', error.message)
    }
    router.push('/login')
  }

  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <Button variant="outline" onClick={handleSignOut} disabled={signingOut}>
          {signingOut ? 'Saindo…' : 'Sair'}
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        Placeholder do 4.0. As telas reais (empresas, configuração do tenant,
        conversas) entram a partir do 4.1.
      </p>
    </div>
  )
}
