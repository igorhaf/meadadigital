'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { use } from 'react'

import { Button } from '@/components/ui/button'
import { getInvitationPublic } from '@/lib/api/invitations'

/**
 * Página PÚBLICA do convite (camada 5.16 #6), fora de (protected) — não exige sessão. Faz
 * o lookup público (GET /api/invitations/{token}) e mostra "Você foi convidado pela X".
 * O botão "Aceitar convite" leva a /login?invite={token}, onde o convidado cria conta /
 * loga e o accept é disparado.
 *
 * Next 16: params é Promise — desembrulhado com use().
 */
export default function InvitePage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params)

  const { data, isPending, isError } = useQuery({
    queryKey: ['public-invitation', token],
    queryFn: () => getInvitationPublic(token),
    retry: false,
  })

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl border bg-background p-6 shadow-sm">
        {isPending && <p className="text-sm text-muted-foreground">Carregando convite…</p>}

        {!isPending && (isError || !data) && (
          <>
            <h1 className="mb-2 text-lg font-semibold">Convite inválido</h1>
            <p className="mb-6 text-sm text-muted-foreground">
              Este convite não foi encontrado, já foi usado ou expirou.
            </p>
            <Link href="/login">
              <Button variant="outline" className="w-full">
                Ir para o login
              </Button>
            </Link>
          </>
        )}

        {!isPending && data && (
          <>
            <h1 className="mb-1 text-lg font-semibold">Você foi convidado</h1>
            <p className="mb-4 text-sm text-muted-foreground">
              A empresa <span className="font-medium text-foreground">{data.companyName}</span> te
              convidou para administrar o painel no WhatsApp.
            </p>
            <p className="mb-6 text-sm text-muted-foreground">
              Convite para <span className="font-medium text-foreground">{data.email}</span>. Use
              este email ao entrar ou criar sua conta.
            </p>
            <Link href={`/login?invite=${token}`}>
              <Button className="w-full">Aceitar convite</Button>
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
