'use client'

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { getUsers, type AdminUserListItem } from '@/lib/api/admin/users'
import { ApiError } from '@/lib/api/client'

const PAGE_SIZE = 20

/**
 * Usuários globais da plataforma (camada 6.2, super-admin). Lista paginada cross-tenant
 * com filtro por email e por status. 403 (tenant tentou a rota) é tratado inline — o
 * backend é a barreira de autorização (mesmo padrão da tela de empresas).
 */
export default function UsersPage() {
  const [q, setQ] = useState('')
  const [applied, setApplied] = useState('')
  const [status, setStatus] = useState<'' | 'active' | 'suspended'>('')
  const [page, setPage] = useState(0)

  const suspended = status === '' ? undefined : status === 'suspended'

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['admin-users', applied, suspended, page],
    queryFn: () => getUsers({ q: applied || undefined, suspended, page, pageSize: PAGE_SIZE }),
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

  const columns: Column<AdminUserListItem>[] = [
    {
      key: 'email',
      header: 'Email',
      render: (u) => (
        <Link href={`/dashboard/users/${u.id}`} className="font-medium hover:underline">
          {u.email}
        </Link>
      ),
    },
    { key: 'companyName', header: 'Empresa' },
    { key: 'role', header: 'Papel', render: (u) => <Badge variant="muted">{u.role}</Badge> },
    {
      key: 'suspended',
      header: 'Status',
      render: (u) =>
        u.suspended ? (
          <Badge variant="danger">suspenso</Badge>
        ) : (
          <Badge variant="success">ativo</Badge>
        ),
    },
    {
      key: 'lastLoginAt',
      header: 'Último login',
      render: (u) => (u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString('pt-BR') : '—'),
    },
  ]

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="space-y-6">
      <PageHeader title="Usuários" description="Usuários de todas as empresas da plataforma." />

      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          setApplied(q)
          setPage(0)
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Email</label>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="buscar email…"
            className="w-64 rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Status</label>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as '' | 'active' | 'suspended')
              setPage(0)
            }}
            className="rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">Todos</option>
            <option value="active">Ativos</option>
            <option value="suspended">Suspensos</option>
          </select>
        </div>
        <Button type="submit" variant="outline">
          Buscar
        </Button>
      </form>

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar usuários.</p>
      ) : (
        <>
          <DataTable<AdminUserListItem>
            data={data?.items ?? []}
            columns={columns}
            loading={isPending}
            emptyMessage="Nenhum usuário encontrado."
          />
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Página {page + 1} de {totalPages} · {total} usuário(s)
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
