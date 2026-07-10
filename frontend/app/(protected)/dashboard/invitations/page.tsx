'use client'

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import {
  getAllInvitations,
  revokeInvitation,
  type AdminInvitation,
} from '@/lib/api/admin/invitations'
import { ApiError } from '@/lib/api/client'

const PAGE_SIZE = 20

const STATUS_VARIANT: Record<
  AdminInvitation['status'],
  'success' | 'muted' | 'danger' | 'warning'
> = {
  pending: 'success',
  accepted: 'muted',
  expired: 'warning',
  revoked: 'danger',
}

/**
 * Convites globais (cross-tenant) do SaaS (camada 6.2, super-admin). Lista paginada com
 * filtro por status + revogar. 403 inline (backend é a barreira).
 */
export default function InvitationsPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [toRevoke, setToRevoke] = useState<AdminInvitation | null>(null)

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['admin-invitations', status, page],
    queryFn: () => getAllInvitations({ status: status || undefined, page, pageSize: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  const revoke = useMutation({
    mutationFn: (id: string) => revokeInvitation(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-invitations'] })
      setToRevoke(null)
    },
    onError: (e) => console.error('revokeInvitation failed:', e),
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

  const columns: Column<AdminInvitation>[] = [
    { key: 'email', header: 'Convidado' },
    { key: 'companyName', header: 'Empresa' },
    {
      key: 'status',
      header: 'Status',
      render: (i) => <Badge variant={STATUS_VARIANT[i.status]}>{i.status}</Badge>,
    },
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
  ]

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="space-y-6">
      <PageHeader title="Convites" description="Convites de acesso de todas as empresas." />

      <div className="flex items-end gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Status</label>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setPage(0)
            }}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">Todos</option>
            <option value="pending">Pendentes</option>
            <option value="accepted">Aceitos</option>
            <option value="expired">Expirados</option>
            <option value="revoked">Revogados</option>
          </select>
        </div>
      </div>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar convites.</p>
      ) : (
        <>
          <DataTable<AdminInvitation>
            data={data?.items ?? []}
            columns={columns}
            loading={isPending}
            emptyMessage="Nenhum convite encontrado."
            actions={(i) =>
              i.status === 'pending' ? (
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  onClick={() => setToRevoke(i)}
                >
                  Revogar
                </Button>
              ) : (
                <span className="text-xs text-muted-foreground">—</span>
              )
            }
          />
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Página {page + 1} de {totalPages} · {total} convite(s)
              </span>
              <div className="flex gap-1">
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  ←
                </Button>
                <Button
                  variant="outline"
                  className="h-7 px-2 text-xs"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  →
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <AlertDialog
        open={toRevoke != null}
        onOpenChange={(o) => !o && setToRevoke(null)}
        title="Revogar convite"
        description={`O convite para ${toRevoke?.email ?? ''} deixará de ser válido.`}
        confirmLabel="Revogar"
        loading={revoke.isPending}
        onConfirm={() => toRevoke && revoke.mutate(toRevoke.id)}
      />
    </div>
  )
}
