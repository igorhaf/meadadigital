'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import { DataTable, type Column } from '@/components/ui/data-table'
import { PageSkeleton } from '@/components/ui/skeleton'
import {
  deleteUser,
  getUser,
  reactivateUser,
  resetUserPassword,
  suspendUser,
  type AdminUserAction,
} from '@/lib/api/admin/users'
import { ApiError } from '@/lib/api/client'

const actionColumns: Column<AdminUserAction & { id: string }>[] = [
  {
    key: 'createdAt',
    header: 'Quando',
    render: (a) => new Date(a.createdAt).toLocaleString('pt-BR'),
  },
  { key: 'action', header: 'Ação' },
  {
    key: 'payload',
    header: 'Detalhes',
    render: (a) => <span className="text-muted-foreground">{a.payload ?? '—'}</span>,
  },
]

/**
 * Detalhe de um usuário global (camada 6.2, super-admin). Dados + empresa + histórico de
 * ações do admin sobre ele + ações destrutivas (suspender/reativar/reset/excluir) via
 * AlertDialog. 403 tratado inline (backend é a barreira).
 */
export default function UserDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<'suspend' | 'reactivate' | 'delete' | null>(null)
  const [resetMsg, setResetMsg] = useState<string | null>(null)

  const {
    data: user,
    isPending,
    isError,
    error,
  } = useQuery({
    queryKey: ['admin-user', id],
    queryFn: () => getUser(id),
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['admin-user', id] })
    queryClient.invalidateQueries({ queryKey: ['admin-users'] })
  }

  const suspend = useMutation({
    mutationFn: () => suspendUser(id),
    onSuccess: () => {
      invalidate()
      setDialog(null)
    },
    onError: (e) => console.error('suspendUser failed:', e),
  })
  const reactivate = useMutation({
    mutationFn: () => reactivateUser(id),
    onSuccess: () => {
      invalidate()
      setDialog(null)
    },
    onError: (e) => console.error('reactivateUser failed:', e),
  })
  const remove = useMutation({
    mutationFn: () => deleteUser(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      router.push('/dashboard/users')
    },
    onError: (e) => console.error('deleteUser failed:', e),
  })
  const reset = useMutation({
    mutationFn: () => resetUserPassword(id),
    onSuccess: () => setResetMsg('Reset enviado.'),
    onError: (e) => {
      const reason = e instanceof ApiError ? e.reason : 'unknown'
      setResetMsg(
        reason === 'service_role_key_not_configured'
          ? 'Reset por admin indisponível: configure SUPABASE_SERVICE_ROLE_KEY no servidor.'
          : 'Erro ao solicitar reset.',
      )
    },
  })

  if (isError && error instanceof ApiError && error.status === 403) {
    return (
      <div className="space-y-6">
        <PageHeader title="Acesso restrito" description="Esta área é restrita ao super-admin." />
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isPending) {
    return <PageSkeleton />
  }

  if (isError || !user) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Usuário"
          breadcrumb={[{ label: 'Usuários', href: '/dashboard/users' }, { label: 'Usuário' }]}
        />
        <p className="text-sm text-destructive">Usuário não encontrado.</p>
      </div>
    )
  }

  const actionsWithId = user.recentActions.map((a, i) => ({ ...a, id: String(i) }))

  return (
    <div className="space-y-6">
      <PageHeader
        title={user.email}
        breadcrumb={[{ label: 'Usuários', href: '/dashboard/users' }, { label: user.email }]}
        actions={
          <div className="flex flex-wrap gap-2">
            {user.suspended ? (
              <Button variant="outline" onClick={() => setDialog('reactivate')}>
                Reativar
              </Button>
            ) : (
              <Button variant="outline" onClick={() => setDialog('suspend')}>
                Suspender
              </Button>
            )}
            <Button
              variant="outline"
              onClick={() => {
                setResetMsg(null)
                reset.mutate()
              }}
            >
              Resetar senha
            </Button>
            <Button variant="destructive" onClick={() => setDialog('delete')}>
              Excluir
            </Button>
          </div>
        }
      />

      {resetMsg && <p className="text-sm text-muted-foreground">{resetMsg}</p>}

      <Section title="Dados">
        <Card className="space-y-3">
          <Field label="Email" value={user.email} />
          <Field label="Papel" value={user.role} />
          <div>
            <dt className="text-xs text-muted-foreground uppercase">Status</dt>
            <dd className="mt-0.5">
              {user.suspended ? (
                <Badge variant="danger">
                  suspenso{user.suspendedReason ? ` — ${user.suspendedReason}` : ''}
                </Badge>
              ) : (
                <Badge variant="success">ativo</Badge>
              )}
            </dd>
          </div>
          <Field
            label="Último login"
            value={user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('pt-BR') : '—'}
          />
          <Field label="Criado em" value={new Date(user.createdAt).toLocaleString('pt-BR')} />
        </Card>
      </Section>

      <Section title="Empresa">
        <Card>
          <Link
            href={`/dashboard/companies/${user.companyId}`}
            className="font-medium hover:underline"
          >
            {user.companyName}
          </Link>
        </Card>
      </Section>

      <Section title="Histórico de ações" description="Ações do super-admin sobre este usuário.">
        <DataTable<AdminUserAction & { id: string }>
          data={actionsWithId}
          columns={actionColumns}
          emptyMessage="Nenhuma ação registrada."
        />
      </Section>

      <AlertDialog
        open={dialog === 'suspend'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Suspender usuário"
        description="O usuário não conseguirá acessar o painel até ser reativado."
        confirmLabel="Suspender"
        loading={suspend.isPending}
        onConfirm={() => suspend.mutate()}
      />
      <AlertDialog
        open={dialog === 'reactivate'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Reativar usuário"
        description="O usuário voltará a ter acesso ao painel."
        confirmLabel="Reativar"
        destructive={false}
        loading={reactivate.isPending}
        onConfirm={() => reactivate.mutate()}
      />
      <AlertDialog
        open={dialog === 'delete'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Excluir usuário"
        description="O usuário será removido (soft delete) e sairá das listas. Esta ação é irreversível pelo painel."
        confirmLabel="Excluir"
        confirmText={user.email}
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm font-medium">{value}</dd>
    </div>
  )
}
