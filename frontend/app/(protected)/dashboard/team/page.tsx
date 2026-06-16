'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { CreateInvitationDialog } from '@/components/create-invitation-dialog'
import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { getMe } from '@/lib/api/me'
import { cancelInvitation, getMyInvitations, type Invitation } from '@/lib/api/invitations'
import { getMyTeamMembers, type TeamMember } from '@/lib/supabase/team'

/** Deriva o status de exibição de um convite a partir de usedAt/expiresAt. */
function invitationStatus(inv: Invitation): { label: string; variant: 'success' | 'muted' | 'danger' } {
  if (inv.usedAt) {
    return { label: 'usado', variant: 'muted' }
  }
  if (new Date(inv.expiresAt) < new Date()) {
    return { label: 'expirado', variant: 'danger' }
  }
  return { label: 'ativo', variant: 'success' }
}

const memberColumns: Column<TeamMember>[] = [
  { key: 'email', header: 'Email' },
  {
    key: 'role',
    header: 'Papel',
    render: (m) => <Badge variant={m.role === 'agent' ? 'muted' : 'default'}>{m.role}</Badge>,
  },
  {
    key: 'createdAt',
    header: 'Desde',
    render: (m) => new Date(m.createdAt).toLocaleDateString('pt-BR'),
  },
]

/**
 * Gerenciamento de equipe do tenant (camada 5.16 #6): membros atuais + convites de admin
 * extra. Super-admin não usa: redireciona para /dashboard. Criar convite gera um link que
 * o admin copia e envia; o convidado aceita em /invite/{token}.
 */
export default function TeamPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data: members, isPending: membersPending } = useQuery({
    queryKey: ['my-team-members'],
    queryFn: getMyTeamMembers,
    enabled: isTenant,
  })

  const { data: invitations, isPending: invitationsPending, isError } = useQuery({
    queryKey: ['my-invitations'],
    queryFn: getMyInvitations,
    enabled: isTenant,
  })

  const cancel = useMutation({
    mutationFn: (id: string) => cancelInvitation(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-invitations'] }),
    onError: (err) => console.error('cancelInvitation failed:', err),
  })

  const invitationColumns: Column<Invitation>[] = [
    { key: 'email', header: 'Email' },
    {
      key: 'createdAt',
      header: 'Criado em',
      render: (i) => new Date(i.createdAt).toLocaleDateString('pt-BR'),
    },
    {
      key: 'expiresAt',
      header: 'Expira em',
      render: (i) => new Date(i.expiresAt).toLocaleDateString('pt-BR'),
    },
    {
      key: 'status',
      header: 'Status',
      render: (i) => {
        const s = invitationStatus(i)
        return <Badge variant={s.variant}>{s.label}</Badge>
      },
    },
  ]

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-5xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Equipe</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>

      {/* Membros atuais */}
      <section className="mb-8">
        <h2 className="mb-3 text-sm font-medium">Membros atuais</h2>
        <DataTable<TeamMember>
          data={members ?? []}
          columns={memberColumns}
          loading={membersPending}
          emptyMessage="Nenhum membro encontrado."
        />
      </section>

      {/* Convites pendentes */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-medium">Convites</h2>
          <Button onClick={() => setDialogOpen(true)}>Novo convite</Button>
        </div>
        {isError ? (
          <p className="text-sm text-destructive">Erro ao carregar convites.</p>
        ) : (
          <DataTable<Invitation>
            data={invitations ?? []}
            columns={invitationColumns}
            loading={invitationsPending}
            emptyMessage="Nenhum convite ainda. Clique em “Novo convite” para gerar um link."
            actions={(i) =>
              !i.usedAt && new Date(i.expiresAt) >= new Date() ? (
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={cancel.isPending && cancel.variables === i.id}
                  onClick={() => {
                    if (confirm(`Cancelar o convite para ${i.email}?`)) {
                      cancel.mutate(i.id)
                    }
                  }}
                >
                  Cancelar
                </Button>
              ) : (
                <span className="text-xs text-muted-foreground">—</span>
              )
            }
          />
        )}
      </section>

      <CreateInvitationDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </div>
  )
}
