'use client'

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { getAdminActions, type AdminActionRow } from '@/lib/api/admin/audit'
import { ApiError } from '@/lib/api/client'

const PAGE_SIZE = 20

/**
 * Ações do super-admin (admin_action_log) — camada 6.5. Rastro de suspensões, edições,
 * exclusões e revogações. READ-only. 403 inline (backend é a barreira). O super-admin não
 * tem linha em users, então mostramos o UUID truncado, não email.
 */
export default function AdminActionsPage() {
  const [action, setAction] = useState('')
  const [targetType, setTargetType] = useState('')
  const [page, setPage] = useState(0)

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['admin-actions', action, targetType, page],
    queryFn: () =>
      getAdminActions({
        action: action || undefined,
        targetType: targetType || undefined,
        page,
        pageSize: PAGE_SIZE,
      }),
    placeholderData: keepPreviousData,
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

  /** Link para o detalhe do alvo, quando aplicável (company/user). */
  function targetLink(r: AdminActionRow) {
    if (!r.targetId) return <span className="text-muted-foreground">—</span>
    const short = r.targetId.slice(0, 8)
    if (r.targetType === 'company') {
      return (
        <Link href={`/dashboard/companies/${r.targetId}`} className="hover:underline">
          {r.targetType} {short}
        </Link>
      )
    }
    if (r.targetType === 'user') {
      return (
        <Link href={`/dashboard/users/${r.targetId}`} className="hover:underline">
          {r.targetType} {short}
        </Link>
      )
    }
    return (
      <span>
        {r.targetType} {short}
      </span>
    )
  }

  const columns: Column<AdminActionRow>[] = [
    {
      key: 'createdAt',
      header: 'Quando',
      render: (r) => new Date(r.createdAt).toLocaleString('pt-BR'),
    },
    {
      key: 'superAdminUserId',
      header: 'Super-admin',
      render: (r) => (
        <span className="font-mono text-xs text-muted-foreground">
          {r.superAdminUserId.slice(0, 8)}
        </span>
      ),
    },
    { key: 'action', header: 'Ação', render: (r) => <Badge variant="muted">{r.action}</Badge> },
    { key: 'target', header: 'Alvo', render: targetLink },
    {
      key: 'payload',
      header: 'Detalhes',
      render: (r) => (
        <span className="line-clamp-1 text-xs text-muted-foreground">
          {r.payload && JSON.stringify(r.payload) !== '{}' ? JSON.stringify(r.payload) : '—'}
        </span>
      ),
    },
  ]

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Ações de admin"
        description="Rastro de ações do super-admin sobre a plataforma."
      />

      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          setPage(0)
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Ação</label>
          <input
            value={action}
            onChange={(e) => {
              setAction(e.target.value)
              setPage(0)
            }}
            placeholder="COMPANY_SUSPENDED…"
            className="w-56 rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">
            Tipo de alvo
          </label>
          <select
            value={targetType}
            onChange={(e) => {
              setTargetType(e.target.value)
              setPage(0)
            }}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">Todos</option>
            <option value="company">Empresa</option>
            <option value="user">Usuário</option>
            <option value="invitation">Convite</option>
            <option value="note">Nota</option>
          </select>
        </div>
      </form>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar ações.</p>
      ) : (
        <>
          <DataTable<AdminActionRow>
            data={data?.items ?? []}
            columns={columns}
            loading={isPending}
            emptyMessage="Nenhuma ação registrada."
          />
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Página {page + 1} de {totalPages} · {total} ação(ões)
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
    </div>
  )
}
